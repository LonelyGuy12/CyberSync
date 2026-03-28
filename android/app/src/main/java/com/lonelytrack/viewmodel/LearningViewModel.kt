package com.lonelytrack.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.lonelytrack.api.RetrofitClient
import com.lonelytrack.model.DailyTask
import com.lonelytrack.model.GeneratePlanResponse
import com.lonelytrack.model.StatusUpdate
import com.lonelytrack.model.UserRequest
import kotlinx.coroutines.launch

class LearningViewModel : ViewModel() {

    private val api = RetrofitClient.api
    private val firestoreDb = FirebaseFirestore.getInstance()

    // ── UI State ────────────────────────────────────────────────────────────

    private val _plan = MutableLiveData<GeneratePlanResponse?>()
    val plan: LiveData<GeneratePlanResponse?> = _plan

    private val _schedule = MutableLiveData<List<DailyTask>>()
    val schedule: LiveData<List<DailyTask>> = _schedule

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var firestoreListener: ListenerRegistration? = null

    // ── Generate a new plan via the backend ─────────────────────────────────

    fun generatePlan(userId: String, topic: String, dailyMinutes: Int, skillLevel: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val request = UserRequest(userId, topic, dailyMinutes, skillLevel)
                val response = api.generatePlan(request)
                _plan.value = response
                _schedule.value = response.schedule

                // Start listening for real-time Firestore updates on this plan
                listenToPlan(response.planId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to generate plan"
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Mark a day completed / missed via the backend ───────────────────────

    fun updateDayStatus(userId: String, planId: String, day: Int, status: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                val request = StatusUpdate(userId, planId, day, status)
                api.updateStatus(request)
                // Firestore listener will automatically push the updated schedule
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update status"
            }
        }
    }

    // ── Real-time Firestore listener for schedule changes ───────────────────

    fun listenToPlan(planId: String) {
        // Remove any previous listener
        firestoreListener?.remove()

        firestoreListener = firestoreDb
            .collection("learning_plans")
            .document(planId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    _error.value = e.message
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val rawSchedule = snapshot.get("schedule") as? List<*> ?: return@addSnapshotListener
                    val tasks = rawSchedule.mapNotNull { item ->
                        val map = item as? Map<*, *> ?: return@mapNotNull null
                        DailyTask(
                            day = (map["day"] as? Number)?.toInt() ?: 0,
                            topic = map["topic"] as? String ?: "",
                            durationMins = (map["duration_mins"] as? Number)?.toInt() ?: 0,
                            status = map["status"] as? String ?: "pending"
                        )
                    }
                    _schedule.value = tasks
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        firestoreListener?.remove()
    }
}
