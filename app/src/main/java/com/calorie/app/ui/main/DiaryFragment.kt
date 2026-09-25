package com.calorie.app.ui.main

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.calorie.app.R
import com.calorie.app.data.local.model.DiaryEntry
import com.calorie.app.data.model.FoodItem
import com.calorie.app.data.model.Foods
import com.calorie.app.data.vision.NutritionEstimate
import com.calorie.app.data.vision.RouterVisionSource
import com.calorie.app.data.vision.VisionSource
import com.calorie.app.databinding.DialogEntryFormBinding
import com.calorie.app.databinding.DialogPickFoodBinding
import com.calorie.app.databinding.FragmentDiaryBinding
import com.calorie.app.util.Macros
import com.calorie.app.util.macroTargets
import com.calorie.app.util.macrosForGrams
import com.calorie.app.util.scalePortion
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Вкладка «Дневник»: норма на день, прогресс, группировка по приёмам пищи и добавление по фото. */
class DiaryFragment : Fragment() {

    private var _b: FragmentDiaryBinding? = null
    private val b get() = _b!!

    private val vm: DiaryViewModel by viewModels()

    private val adapter = DiaryGroupAdapter(
        onDelete = { entry -> deleteWithUndo(entry) },
        onAddToMeal = { meal -> showFoodPicker(meal) }
    )

    private var vision: VisionSource? = null

    /** URI кэш-файла для съёмки с камеры (TakePicture пишет прямо в него). */
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null

    // ---------- Launcher-ы ----------

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            android.util.Log.d("CalorieVision", "pick resultCode=" + res.resultCode + " data=" + res.data?.data)
            val uri = res.data?.data
                ?: run {
                    android.util.Log.e("CalorieVision", "pick returned null uri")
                    return@registerForActivityResult
                }
            estimateFromUri(uri)
        }

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                val uri = pendingCameraUri
                val file = pendingCameraFile
                if (uri != null && file != null) estimateFromUri(uri, file) else {
                    Toast.makeText(requireContext(), R.string.diary_camera_failed, Toast.LENGTH_LONG).show()
                }
            }
            pendingCameraUri = null
            pendingCameraFile = null
        }

    // ---------- Lifecycle ----------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentDiaryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        vision = RouterVisionSource()

        b.btnPrevDay.setOnClickListener { vm.goToPrev() }
        b.btnNextDay.setOnClickListener { vm.goToNext() }
        b.btnToday.setOnClickListener { vm.goToToday() }

        // Свайпы вправо/влево = те же стрелки по дням. Влево в сегодняшнем дне
        // ничего не делает: будущее закрыто на уровне ViewModel.
        b.scroll.onSwipeRight = { vm.goToPrev(); true }
        b.scroll.onSwipeLeft = {
            if (vm.canGoNext.value) { vm.goToNext(); true } else false
        }

        b.fabCamera.setOnClickListener { startCamera() }
        b.fabGallery.setOnClickListener { openGallery() }

        observeVm()
    }

    private fun observeVm() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.dayTitle.collect { t -> b.txtDay.text = t } }
                launch { vm.canGoNext.collect { e -> b.btnNextDay.isEnabled = e } }
                launch { vm.isToday.collect { today -> applyDayState(today) } }
                launch { renderSummary() }
                launch { renderEntries() }
            }
        }
    }

    /** Сегодня — можно добавлять; в прошлом дне вместо кнопок показываем «Сегодня». */
    private fun applyDayState(today: Boolean) {
        val state = if (today) View.VISIBLE else View.GONE
        b.fabCamera.visibility = state
        b.fabGallery.visibility = state
        b.btnToday.visibility = if (today) View.GONE else View.VISIBLE
    }

    private fun deleteWithUndo(entry: DiaryEntry) {
        vm.delete(entry.id)
        // LENGTH_LONG — всего ~2.7 с: за это время до «Отменить» не дотянуться,
        // а промах по ней уводит на соседнюю кнопку навбара. Даём 6 с.
        Snackbar.make(b.root, getString(R.string.entry_deleted, entry.name), Snackbar.LENGTH_LONG)
            .setDuration(UNDO_DURATION_MS)
            .setAction(R.string.undo_action) { vm.restore(entry) }
            .show()
    }

    private suspend fun renderSummary() {
        vm.summary.collect { s ->
            val over = s.remainingKcal < 0
            b.txtTitleLeft.setText(if (over) R.string.diary_over else R.string.diary_remaining)
            b.txtEaten.text = if (over) {
                "+%d".format(Locale.US, -s.remainingKcal.toInt())
            } else {
                "%d".format(Locale.US, s.remainingKcal.toInt())
            }
            b.txtTarget.text = getString(R.string.diary_of_target, s.targetKcal)
            b.ringProgress.setProgress(s.progress)
            // При переборе нормы кольцо краснеет — видно с одного взгляда.
            b.ringProgress.setProgressColor(
                color(if (over) R.color.danger else R.color.calorie_primary)
            )
            val m = macroTargets(s.targetKcal)
            bindMacro(b.txtProtein, b.barProtein, s.protein, m.protein)
            bindMacro(b.txtFat, b.barFat, s.fat, m.fat)
            bindMacro(b.txtCarbs, b.barCarbs, s.carbs, m.carbs)
        }
    }

    /** Съедено / норма и заполнение полосы макроса. Нулевая норма — полоса пустая. */
    private fun bindMacro(value: TextView, bar: ProgressBar, eaten: Double, target: Double) {
        value.text = "%.0f / %.0f г".format(Locale.US, eaten, target)
        bar.progress = if (target > 0) {
            ((eaten / target) * 100.0).coerceIn(0.0, 100.0).toInt()
        } else {
            0
        }
    }

    private fun color(res: Int): Int = ContextCompat.getColor(requireContext(), res)

    /**
     * Таблица приёмов пищи всегда непустая (4 слота), поэтому никаких «пустых
     * состояний» и переключателей видимости здесь нет: вид строки — включая
     * кнопку «+», которая есть только у сегодняшнего дня, — задаёт сама модель
     * [DiaryRow], а не отдельный флаг во фрагменте.
     */
    private suspend fun renderEntries() {
        vm.grouped.collect { rows ->
            adapter.submitList(rows)
        }
    }

    // ---------- Фото: камера и галерея ----------

    private fun startCamera() {
        val dir = File(requireContext().filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", file
        )
        pendingCameraUri = uri
        pendingCameraFile = file
        takePhoto.launch(uri)
    }

    private fun openGallery() {
        val g = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        pickPhoto.launch(
            android.content.Intent.createChooser(g, getString(R.string.diary_pick_photo))
        )
    }

    /** Галерея: копируем урл-фото в постоянную папку и распознаём. */
    private fun estimateFromUri(uri: Uri, target: File? = null) {
        val file = target
            ?: File(requireContext().filesDir, "photos/gallery_${System.currentTimeMillis()}.jpg")
        file.parentFile?.mkdirs()
        val busy = Dialog(requireContext())
        busy.setTitle(R.string.diary_analyzing)
        busy.setCancelable(false)
        busy.show()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val est = withContext(Dispatchers.IO) {
                    val bmp = decodeSample(uri, 1100.0)
                    writeBmp(bmp, file)
                    vision?.estimate(file)
                }
                busy.dismiss()
                when {
                    est == null ->
                        Toast.makeText(requireContext(), R.string.vision_not_ready, Toast.LENGTH_LONG).show()
                    !est.ok ->
                        Toast.makeText(requireContext(),
                            est.message.ifBlank { getString(R.string.vision_failed) },
                            Toast.LENGTH_LONG).show()
                    else -> showConfirm(est, file.absolutePath)
                }
            } catch (e: Exception) {
                busy.dismiss()
                Toast.makeText(requireContext(),
                    getString(R.string.error_prefix, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun decodeSample(uri: Uri, maxSide: Double): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        requireContext().contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw Exception("Не удалось прочитать изображение")
        }
        val sample = computeSample(bounds.outWidth, bounds.outHeight, maxSide)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return requireContext().contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts)
                ?: throw Exception("Не удалось декодировать изображение")
        }
    }

    private fun writeBmp(bmp: Bitmap, out: File) {
        FileOutputStream(out).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        bmp.recycle()
    }

    private fun computeSample(w: Int, h: Int, maxSide: Double): Int {
        if (w <= 0 || h <= 0) return 1
        val longSide = maxOf(w, h).toDouble()
        var s = 1
        while (longSide / (s * 2) > maxSide) s *= 2
        return s
    }

    // ---------- Диалог подтверждения по фото ----------

    private fun showConfirm(est: NutritionEstimate, photoPath: String) {
        val vb = DialogEntryFormBinding.inflate(layoutInflater)
        vb.lblFor.setText(R.string.entry_field_label)
        vb.txtVisionNote.setText(R.string.entry_vision_note)
        vb.txtVisionNote.visibility = View.VISIBLE
        vb.edtEntryName.setText(est.name)
        vb.lblInfo.setText(R.string.entry_info_per_portion)
        vb.txtPer100.text = "Б %.0f · Ж %.0f · У %.0f (%s)".format(
            Locale.US, est.proteinG, est.fatG, est.carbsG, getString(R.string.entry_portion_whole)
        )
        vb.imgEntryPhoto.loadPhoto(photoPath)
        presetMeal(vb.mealGroup)
        vb.boxGrams.hint = getString(
            if (est.portionG > 0) R.string.entry_amount else R.string.entry_grams_unknown
        )
        if (est.portionG > 0) vb.edtGrams.setText(trimD(est.portionG))

        /** Введённый вес; 0/пусто/нечисло -> оценка модели (или 0, если её нет). */
        fun enteredGrams(): Double {
            val g = vb.edtGrams.text.toString().toDoubleOrNull() ?: 0.0
            return if (g > 0) g else est.portionG
        }

        /** КБЖУ для веса в поле — те же числа, что уйдут в БД. */
        fun current(): Macros =
            scalePortion(est.kcal, est.proteinG, est.fatG, est.carbsG, est.portionG, enteredGrams())

        fun refresh() {
            val m = current()
            val g = enteredGrams()
            val tail = if (g > 0) " · %.0f г".format(Locale.US, g)
            else " · %s".format(getString(R.string.entry_portion_whole))
            vb.txtResult.text = "%d ккал · Б %.0f · Ж %.0f · У %.0f%s".format(
                Locale.US, m.kcal.toInt(), m.protein, m.fat, m.carbs, tail
            )
        }
        vb.edtGrams.onChange { refresh() }
        refresh()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_by_photo)
            .setView(vb.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val name = vb.edtEntryName.text.toString().trim().ifBlank { est.name }
                val m = current()
                val grams = enteredGrams()
                val meal = selectedMeal(vb.mealGroup)
                vm.add(
                    name = name, grams = grams,
                    kcal = m.kcal, protein = m.protein,
                    fat = m.fat, carbs = m.carbs,
                    photoPath = photoPath, meal = meal
                )
                Toast.makeText(requireContext(), R.string.entry_added_toast, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- Ручное добавление: пикер продуктов ----------

    private var pickerDialog: android.app.Dialog? = null

    /** [meal] — слот, из которого нажали «+»; null — добавить в текущий по времени. */
    private fun showFoodPicker(meal: String? = null) {
        val vb = DialogPickFoodBinding.inflate(layoutInflater)
        val foodAdapter = FoodAdapter { food ->
            pickerDialog?.dismiss()
            pickerDialog = null
            showManualConfirm(food, meal)
        }

        vb.recyclerFoods.layoutManager = LinearLayoutManager(requireContext())
        vb.recyclerFoods.adapter = foodAdapter
        foodAdapter.submitList(Foods.common)

        vb.edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val q = s.toString().trim()
                foodAdapter.submitList(
                    if (q.isEmpty()) Foods.common
                    else Foods.common.filter { it.name.contains(q, ignoreCase = true) }
                )
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        vb.btnCustomFood.setOnClickListener {
            pickerDialog?.dismiss()
            pickerDialog = null
            showCustomFoodConfirm(meal)
        }

        dialogPick(vb.root)
    }

    /** Пересчёт per-100 -> порция для известного продукта. */
    private fun showManualConfirm(food: FoodItem, meal: String? = null) {
        val vb = DialogEntryFormBinding.inflate(layoutInflater)
        vb.lblFor.setText(R.string.entry_field_label)
        vb.edtEntryName.setText(food.name)
        vb.txtPer100.text = "Б %.1f · Ж %.1f · У %.1f %s".format(
            Locale.US, food.proteinPer100, food.fatPer100, food.carbsPer100,
            getString(R.string.entry_per_100_tail)
        )
        vb.txtVisionNote.visibility = View.GONE
        vb.edtGrams.setText("100")
        presetMeal(vb.mealGroup, meal)

        fun refresh() {
            val g = vb.edtGrams.text.toString().toDoubleOrNull() ?: 0.0
            val m = macrosForGrams(
                food.kcalPer100.toDouble(), food.proteinPer100, food.fatPer100, food.carbsPer100, g
            )
            vb.txtResult.text = "%d ккал (%.0f г)".format(Locale.US, m.kcal.toInt(), g)
        }
        vb.edtGrams.onChange { refresh() }
        refresh()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_manual)
            .setView(vb.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val name = vb.edtEntryName.text.toString().trim().ifBlank { food.name }
                val g = vb.edtGrams.text.toString().toDoubleOrNull() ?: 0.0
                val m = macrosForGrams(
                    food.kcalPer100.toDouble(), food.proteinPer100, food.fatPer100, food.carbsPer100, g
                )
                vm.add(
                    name = name, grams = g,
                    kcal = m.kcal, protein = m.protein,
                    fat = m.fat, carbs = m.carbs,
                    meal = selectedMeal(vb.mealGroup)
                )
                Toast.makeText(requireContext(), R.string.entry_added_toast, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /** Ручное добавление: полностью свой продукт (КБЖУ на 100 г). */
    private fun showCustomFoodConfirm(meal: String? = null) {
        val vb = DialogEntryFormBinding.inflate(layoutInflater)
        vb.lblFor.setText(R.string.entry_field_label)
        vb.txtVisionNote.visibility = View.GONE
        vb.lblInfo.setText(R.string.entry_info_per_100)
        vb.txtPer100.setText(R.string.entry_custom_product)
        vb.lnlCustomMacros.visibility = View.VISIBLE
        vb.edtGrams.setText("100")
        presetMeal(vb.mealGroup, meal)

        fun refreshedValues(): DoubleArray {
            val k = vb.edtCustomKcal.text.toString().toDoubleOrNull() ?: 0.0
            val p = vb.edtCustomProtein.text.toString().toDoubleOrNull() ?: 0.0
            val f = vb.edtCustomFat.text.toString().toDoubleOrNull() ?: 0.0
            val c = vb.edtCustomCarbs.text.toString().toDoubleOrNull() ?: 0.0
            return doubleArrayOf(k, p, f, c)
        }

        fun current(): Macros {
            val (k_, p, f, c) = refreshedValues()
            val g = vb.edtGrams.text.toString().toDoubleOrNull() ?: 0.0
            return macrosForGrams(k_, p, f, c, g)
        }

        fun refresh() {
            val m = current()
            val g = vb.edtGrams.text.toString().toDoubleOrNull() ?: 0.0
            vb.txtResult.text = "%d ккал · Б %.0f · Ж %.0f · У %.0f (%.0f г)".format(
                Locale.US, m.kcal.toInt(), m.protein, m.fat, m.carbs, g
            )
        }
        listOf(vb.edtCustomKcal, vb.edtCustomProtein, vb.edtCustomFat, vb.edtCustomCarbs)
            .forEach { it.onChange { refresh() } }
        vb.edtGrams.onChange { refresh() }
        refresh()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.entry_custom_product)
            .setView(vb.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val name = vb.edtEntryName.text.toString().trim()
                    .ifBlank { getString(R.string.entry_default_custom_name) }
                val g = vb.edtGrams.text.toString().toDoubleOrNull() ?: 0.0
                val m = current()
                vm.add(
                    name = name, grams = g,
                    kcal = m.kcal, protein = m.protein,
                    fat = m.fat, carbs = m.carbs,
                    meal = selectedMeal(vb.mealGroup)
                )
                Toast.makeText(requireContext(), R.string.entry_added_toast, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ---------- Helpers ----------

    /** [meal] — пришли из «+» конкретного слота; null — берём слот по времени. */
    private fun presetMeal(group: ChipGroup, meal: String? = null) {
        val m = meal ?: vm.defaultMealSlot()
        group.check(when (m) {
            DiaryViewModel.MEAL_SLOTS[0] -> R.id.btnMealBreakfast
            DiaryViewModel.MEAL_SLOTS[1] -> R.id.btnMealLunch
            DiaryViewModel.MEAL_SLOTS[2] -> R.id.btnMealDinner
            else -> R.id.btnMealSnack
        })
    }

    private fun selectedMeal(group: ChipGroup): String =
        when (group.checkedChipId) {
            R.id.btnMealBreakfast -> DiaryViewModel.MEAL_SLOTS[0]
            R.id.btnMealLunch -> DiaryViewModel.MEAL_SLOTS[1]
            R.id.btnMealDinner -> DiaryViewModel.MEAL_SLOTS[2]
            else -> DiaryViewModel.MEAL_SLOTS[3]
        }

    private fun EditText.onChange(cb: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { cb() }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun android.widget.ImageView.loadPhoto(path: String) {
        if (path.isNotBlank() && File(path).let { it.exists() && it.length() > 0 }) {
            visibility = View.VISIBLE
            load(File(path)) {
                crossfade(true)
                placeholder(R.drawable.ic_restaurant)
                error(R.drawable.ic_restaurant)
            }
        } else {
            visibility = View.GONE
        }
    }

    private fun dialogPick(root: View) {
        pickerDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(root)
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun trimD(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(Locale.US, v) else "%.1f".format(Locale.US, v)

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private companion object {
        /** Сколько висит «Отменить» после удаления записи. */
        const val UNDO_DURATION_MS = 6_000
    }
}
