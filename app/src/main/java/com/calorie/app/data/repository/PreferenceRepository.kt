package com.calorie.app.data.repository

import com.calorie.app.data.local.AppDatabase
import com.calorie.app.data.local.model.Preference
import com.calorie.app.data.local.model.PreferenceEvent
import com.calorie.app.domain.preferences.DishPreference
import com.calorie.app.domain.preferences.PreferenceEngine

/** Пишет события и обновляет веса; отдаёт планировщику снимок предпочтений. */
class PreferenceRepository(private val db: AppDatabase) {

    suspend fun record(type: String, dishId: String, now: Long = System.currentTimeMillis()) {
        val signal = PreferenceEngine.signal(type)
        if (signal == 0.0) return

        db.preferenceDao().insertEvent(
            PreferenceEvent(
                type = type,
                entityType = PreferenceEngine.ENTITY_DISH,
                entityId = dishId,
                weight = signal,
                createdAt = now
            )
        )

        val key = PreferenceEngine.keyFor(PreferenceEngine.ENTITY_DISH, dishId)
        val current = db.preferenceDao().byKey(key)?.weight ?: 0.0
        db.preferenceDao().upsert(
            Preference(
                key = key,
                entityType = PreferenceEngine.ENTITY_DISH,
                entityId = dishId,
                weight = PreferenceEngine.update(current, signal),
                updatedAt = now
            )
        )
    }

    /** Веса плюс время последней готовки — планировщику этого достаточно. */
    suspend fun snapshot(now: Long = System.currentTimeMillis()): Map<String, DishPreference> {
        val weights = db.preferenceDao().all().associate { it.entityId to it.weight }
        val lastCooked = HashMap<String, Long>()
        for (event in db.preferenceDao().eventsOfType(PreferenceEngine.TYPE_COOKED)) {
            if (event.createdAt > (lastCooked[event.entityId] ?: 0L)) {
                lastCooked[event.entityId] = event.createdAt
            }
        }
        val ids = weights.keys + lastCooked.keys
        return ids.associateWith { id ->
            DishPreference(weight = weights[id] ?: 0.0, lastCookedAt = lastCooked[id])
        }
    }

    /** Полное забывание блюда: и вес, и события. Обратной силы у этого нет. */
    suspend fun forget(dishId: String) {
        db.preferenceDao().deletePreference(dishId)
        db.preferenceDao().deleteEvents(dishId)
    }
}
