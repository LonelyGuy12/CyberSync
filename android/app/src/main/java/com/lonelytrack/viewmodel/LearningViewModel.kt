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

    private val _updatingDays = MutableLiveData<Set<Int>>(emptySet())
    val updatingDays: LiveData<Set<Int>> = _updatingDays

    private val _totalPoints = MutableLiveData(0)
    val totalPoints: LiveData<Int> = _totalPoints

    private val _pointsEarned = MutableLiveData<Int?>()
    val pointsEarned: LiveData<Int?> = _pointsEarned

    private val _streak = MutableLiveData(0)
    val streak: LiveData<Int> = _streak

    // ── Generate a new plan via the backend ─────────────────────────────────

    fun generatePlan(userId: String, topic: String, dailyMinutes: Int, totalDays: Int, skillLevel: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val request = UserRequest(userId, topic, dailyMinutes, totalDays, skillLevel)
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
        // Prevent double-tap
        if (_updatingDays.value?.contains(day) == true) return
        _updatingDays.value = (_updatingDays.value ?: emptySet()) + day

        viewModelScope.launch {
            _error.value = null
            try {
                val request = StatusUpdate(userId, planId, day, status)
                val response = api.updateStatus(request)

                // Update points
                if (response.pointsEarned > 0) {
                    _totalPoints.value = response.totalPoints
                    _pointsEarned.value = response.pointsEarned
                    _streak.value = response.streak
                }

                // Optimistic local update — immediately reflect in UI
                val current = _schedule.value?.toMutableList() ?: mutableListOf()
                val idx = current.indexOfFirst { it.day == day }
                if (idx >= 0) {
                    current[idx] = current[idx].copy(status = status)
                    _schedule.value = current
                }
            } catch (e: retrofit2.HttpException) {
                val body = e.response()?.errorBody()?.string() ?: e.message()
                _error.value = "Day $day: $body"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update status"
            } finally {
                _updatingDays.value = (_updatingDays.value ?: emptySet()) - day
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

    fun loadPlan(planId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val response = api.getPlan(planId)
                _plan.value = GeneratePlanResponse(
                    planId = response.planId,
                    goal = response.goal,
                    totalDays = response.totalDays,
                    schedule = response.schedule
                )
                _schedule.value = response.schedule
                listenToPlan(response.planId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load plan"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearPlan() {
        firestoreListener?.remove()
        firestoreListener = null
        _plan.value = null
        _schedule.value = emptyList()
        _error.value = null
    }

    fun loadPoints(userId: String) {
        viewModelScope.launch {
            try {
                val response = api.getPoints(userId)
                _totalPoints.value = response.totalPoints
                _streak.value = response.streak
            } catch (_: Exception) {
                // Silent — points are non-critical
            }
        }
    }

    fun clearPointsAnimation() {
        _pointsEarned.value = null
    }
}
