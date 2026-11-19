package com.example.physicslab

import com.example.sandbox.SandboxPreset
import org.json.JSONObject

enum class AcademicYear {
    YEAR_1_L1,
    YEAR_2_L2
}

enum class CurriculumSourceStatus {
    AWAITING_OFFICIAL_PDF,
    OFFICIAL_PDF_IMPORTED
}

enum class ExerciseOrigin {
    SOURCE_PDF,
    GENERATED_PRACTICE
}

data class LabFormula(
    val symbol: String,
    val expression: String,
    val unit: String,
    val noteEn: String,
    val noteAr: String
)

data class LabLesson(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val summaryEn: String,
    val summaryAr: String,
    val sourceStatus: CurriculumSourceStatus
)

data class LabExercise(
    val id: String,
    val promptEn: String,
    val promptAr: String,
    val solutionEn: String,
    val solutionAr: String,
    val origin: ExerciseOrigin
)

data class LabQuizQuestion(
    val id: String,
    val questionEn: String,
    val questionAr: String,
    val optionsEn: List<String>,
    val optionsAr: List<String>,
    val correctIndex: Int,
    val explanationEn: String,
    val explanationAr: String,
    val origin: ExerciseOrigin
)

data class LabModule(
    val id: String,
    val code: String,
    val year: AcademicYear,
    val semester: String,
    val titleEn: String,
    val titleAr: String,
    val sourceStatus: CurriculumSourceStatus,
    val linkedSandboxPreset: SandboxPreset,
    val lessons: List<LabLesson>,
    val formulas: List<LabFormula>,
    val exercises: List<LabExercise>,
    val quiz: List<LabQuizQuestion>
)

/**
 * Algerian University Sciences de la Matière (SM) Year 1 (L1) & Year 2 (L2) Physics Lab catalog.
 * IMPORTANT HONESTY POLICY:
 * - Default modules are explicitly marked AWAITING_OFFICIAL_PDF and GENERATED_PRACTICE.
 * - No fabricated syllabus is ever presented as official Algerian Ministry PDF curriculum.
 * - Includes a working structured JSON course-pack importer so the user can load extracted
 *   official university PDF courses directly into the module catalog.
 */
object PhysicsLabCatalog {

    val defaultModules: List<LabModule> = listOf(
        LabModule(
            id = "sm_l1_mec",
            code = "PHY-L1-S1",
            year = AcademicYear.YEAR_1_L1,
            semester = "S1",
            titleEn = "Point Mass Mechanics (Mécanique du Point)",
            titleAr = "ميكانيك النقطة المادية (السنة الأولى SM)",
            sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF,
            linkedSandboxPreset = SandboxPreset.FREE_FALL,
            lessons = listOf(
                LabLesson(
                    id = "l1_mec_1",
                    titleEn = "Kinematics, Reference Frames & Free Fall",
                    titleAr = "الحركة (Cinématique) والمراجع والسقوط الحر",
                    summaryEn = "Module shell ready for your official L1 SM PDF lecture notes. Covers position vector r(t), velocity v = dr/dt, acceleration a = dv/dt, and constant-g vertical motion.",
                    summaryAr = "هيكل درس جاهز لاستيراد محاضرات PDF الرسمية للسنة الأولى SM. يغطي شعاع الموضع والسرعة والتسارع والسقوط الحر.",
                    sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF
                ),
                LabLesson(
                    id = "l1_mec_2",
                    titleEn = "Newton's Laws, Work & Mechanical Energy",
                    titleAr = "قوانين نيوتن، العمل والطاقة الميكانيكية",
                    summaryEn = "Module shell awaiting official PDF import. Links directly to the 2D Physics Sandbox to observe live Kinetic (Ek) and Potential (Ep) energy exchange.",
                    summaryAr = "هيكل درس بانتظار ملف PDF الرسمي. مرتبط مباشرة بمحاكاة الفيزياء لمراقبة تحول الطاقة الحركية والكامنة.",
                    sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF
                )
            ),
            formulas = listOf(
                LabFormula("ΣF", "ΣF = m · a", "N (kg·m/s²)", "Newton's Second Law", "القانون الثاني لنيوتن"),
                LabFormula("Ek", "Ek = ½ · m · v²", "J (Joule)", "Translational kinetic energy", "الطاقة الحركية الانسحابية"),
                LabFormula("Ep", "Ep = m · g · h", "J (Joule)", "Gravitational potential energy", "الطاقة الكامنة الثقالية")
            ),
            exercises = listOf(
                LabExercise(
                    id = "ex_l1_mec_1",
                    promptEn = "[Generated Practice] A 2.0 kg mass is dropped from rest at h = 5.0 m (g = 9.81 m/s²). Compute its impact speed neglecting air drag.",
                    promptAr = "[تمرين تدريبي] تُسقط كتلة قدرها 2.0 kg من السكون من ارتفاع h = 5.0 m (باعتبار g = 9.81 m/s²). احسب سرعة اصطدامها بإهمال مقاومة الهواء.",
                    solutionEn = "By energy conservation: ½mv² = mgh => v = √(2gh) = √(2 × 9.81 × 5.0) ≈ 9.90 m/s.",
                    solutionAr = "من انحفاظ الطاقة: ½mv² = mgh ومنه v = √(2gh) = √(2 × 9.81 × 5.0) ≈ 9.90 m/s.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            ),
            quiz = listOf(
                LabQuizQuestion(
                    id = "q_l1_mec_1",
                    questionEn = "In vacuum (no air drag), if mass m is doubled while falling from height h, the impact speed v:",
                    questionAr = "في الفراغ (دون مقاومة الهواء)، إذا ضاعفنا الكتلة m عند السقوط من نفس الارتفاع h، فإن سرعة الوصول v:",
                    optionsEn = listOf("Doubles", "Stays unchanged (v = √(2gh))", "Becomes half"),
                    optionsAr = listOf("تتضاعف", "تبقى ثابتة (v = √(2gh))", "تنقص إلى النصف"),
                    correctIndex = 1,
                    explanationEn = "Mass cancels in ½mv² = mgh, so impact velocity depends only on g and h.",
                    explanationAr = "تختصر الكتلة m من طرفي معادلة انحفاظ الطاقة، فتعتمد السرعة فقط على g و h.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            )
        ),
        LabModule(
            id = "sm_l1_elec",
            code = "PHY-L1-S2",
            year = AcademicYear.YEAR_1_L1,
            semester = "S2",
            titleEn = "Electricity & Fields (Électricité)",
            titleAr = "الكهرباء والحقول (السنة الأولى SM)",
            sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF,
            linkedSandboxPreset = SandboxPreset.MAGNET_FIELD,
            lessons = listOf(
                LabLesson(
                    id = "l1_elec_1",
                    titleEn = "Coulomb Force, Field Lines & Dipole Polarity",
                    titleAr = "قوة كولوم، خطوط الحقل والقطبية",
                    summaryEn = "Awaiting official Algerian L1 S2 PDF upload. Test attraction/repulsion field lines interactively in the Magnet Field sandbox preset.",
                    summaryAr = "بانتظار استيراد ملف PDF الرسمي للسداسي الثاني. جرّب الجذب والتنافر التفاعلي في محاكي الحقل المغناطيسي.",
                    sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF
                )
            ),
            formulas = listOf(
                LabFormula("F", "F = k · |q₁q₂| / r²", "N", "Coulomb inverse-square law", "قانون كولوم للتجاذب والتنافر"),
                LabFormula("U", "U = R · I", "V (Volt)", "Ohm's Law for linear resistors", "قانون أوم")
            ),
            exercises = listOf(
                LabExercise(
                    id = "ex_l1_elec_1",
                    promptEn = "[Generated Practice] If the distance r between two point charges is doubled, how does the electrostatic force magnitude change?",
                    promptAr = "[تمرين تدريبي] إذا تضاعفت المسافة r بين شحنتين نقطيتين، كيف تتغير شدة القوة الكهروستاتيكية؟",
                    solutionEn = "Since F ∝ 1/r², doubling r reduces the force to F / 4 (one quarter).",
                    solutionAr = "بما أن القوة تتناسب عكساً مع مربع المسافة 1/r²، فإن مضاعفة المسافة تقلل القوة إلى الربع (F / 4).",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            ),
            quiz = listOf(
                LabQuizQuestion(
                    id = "q_l1_elec_1",
                    questionEn = "If two identical coils switch from ATTRACT to REPEL, the force vector on a ferromagnetic mass:",
                    questionAr = "عند تبديل قطبية الوشيعة من الجذب إلى التنافر، فإن شعاع القوة على الكتلة المعدنية:",
                    optionsEn = listOf("Reverses direction by 180°", "Drops to zero", "Rotates by 90°"),
                    optionsAr = listOf("ينعكس اتجاهه بزاوية 180°", "ينعدم تماماً", "يدور بزاوية 90°"),
                    correctIndex = 0,
                    explanationEn = "Reversing polarity flips the sign of the radial force vector along the line connecting coil and mass.",
                    explanationAr = "عكس القطبية يقلب إشارة شعاع القوة على المحور الواصل بين الوشيعة والكتلة.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            )
        ),
        LabModule(
            id = "sm_l2_vib",
            code = "PHY-L2-S3",
            year = AcademicYear.YEAR_2_L2,
            semester = "S3",
            titleEn = "Vibrations & Waves (Vibrations et Ondes)",
            titleAr = "الاهتزازات والأمواج (السنة الثانية SM)",
            sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF,
            linkedSandboxPreset = SandboxPreset.PENDULUM_LAB,
            lessons = listOf(
                LabLesson(
                    id = "l2_vib_1",
                    titleEn = "Simple Pendulum & Harmonic Oscillators",
                    titleAr = "النواس البسيط والمهتزات التوافقية",
                    summaryEn = "Awaiting official L2 SM PDF notes. Launch the Pendulum Lab preset to observe period T = 2π√(L/g) and damping effects.",
                    summaryAr = "بانتظار ملف PDF الرسمي للسنة الثانية SM. شغّل محاكاة النواس لملاحظة الدور T = 2π√(L/g) والتخامد.",
                    sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF
                )
            ),
            formulas = listOf(
                LabFormula("T₀", "T₀ = 2π · √(L / g)", "s", "Small-angle simple pendulum period", "دور النواس البسيط للزوايا الصغيرة"),
                LabFormula("ω₀", "ω₀ = √(k / m)", "rad/s", "Natural angular frequency", "النبض الذاتي للمهتز التوافقي")
            ),
            exercises = listOf(
                LabExercise(
                    id = "ex_l2_vib_1",
                    promptEn = "[Generated Practice] A simple pendulum has length L = 1.0 m at g = 9.81 m/s². Estimate its small-angle period T₀.",
                    promptAr = "[تمرين تدريبي] نواس بسيط طوله L = 1.0 m عند g = 9.81 m/s². احسب دوره الخاص T₀.",
                    solutionEn = "T₀ = 2π · √(1.0 / 9.81) ≈ 2.01 seconds.",
                    solutionAr = "T₀ = 2π · √(1.0 / 9.81) ≈ 2.01 ثانية.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            ),
            quiz = listOf(
                LabQuizQuestion(
                    id = "q_l2_vib_1",
                    questionEn = "Quadrupling the string length L of a simple pendulum multiplies its period T₀ by:",
                    questionAr = "مضاعفة طول خيط النواس البسيط أربع مرات (4L) تضرب دوره الخاص T₀ في:",
                    optionsEn = listOf("4", "2", "0.5"),
                    optionsAr = listOf("4", "2", "0.5"),
                    correctIndex = 1,
                    explanationEn = "Because T₀ ∝ √L, multiplying L by 4 multiplies T₀ by √4 = 2.",
                    explanationAr = "بما أن الدور يتناسب مع الجذر التربيعي للطول √L، فإن مضاعفة الطول 4 مرات تضاعف الدور مرتين فقط.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            )
        ),
        LabModule(
            id = "sm_l2_analyt",
            code = "PHY-L2-S4",
            year = AcademicYear.YEAR_2_L2,
            semester = "S4",
            titleEn = "Analytical Mechanics & Equilibrium",
            titleAr = "الميكانيك التحليلي والتوازن (السنة الثانية SM)",
            sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF,
            linkedSandboxPreset = SandboxPreset.LEVER_BALANCE,
            lessons = listOf(
                LabLesson(
                    id = "l2_an_1",
                    titleEn = "Torque, Center of Mass & Rotational Equilibrium",
                    titleAr = "العزم ومركز الثقل والتوازن الدوراني",
                    summaryEn = "Awaiting official L2 SM S4 PDF import. Experiment with lever arms and torque equilibrium in the Lever Balance sandbox preset.",
                    summaryAr = "بانتظار ملف PDF الرسمي للسداسي الرابع. اختبر ذراع العزم وتوازن الرافعة مباشرة في محاكي التوازن.",
                    sourceStatus = CurriculumSourceStatus.AWAITING_OFFICIAL_PDF
                )
            ),
            formulas = listOf(
                LabFormula("τ", "τ = r × F = r · F · sin(θ)", "N·m", "Torque about pivot", "عزم القوة بالنسبة لمحور الدوران"),
                LabFormula("L", "L = Ek - Ep", "J", "Lagrangian function", "دالة لاغرانج")
            ),
            exercises = listOf(
                LabExercise(
                    id = "ex_l2_an_1",
                    promptEn = "[Generated Practice] A 2.0 kg mass sits 1.5 m left of a lever pivot. Where should a 3.0 kg mass sit on the right for zero net torque?",
                    promptAr = "[تمرين تدريبي] توضع كتلة 2.0 kg على بعد 1.5 m يسار محور ارتكاز. أين يجب وضع كتلة 3.0 kg يمين المحور لتحقيق التوازن؟",
                    solutionEn = "m₁·d₁ = m₂·d₂ => 2.0 × 1.5 = 3.0 × d₂ => d₂ = 1.0 m right of the pivot.",
                    solutionAr = "من شرط توازن العزوم m₁·d₁ = m₂·d₂ نجد d₂ = (2.0 × 1.5) / 3.0 = 1.0 m يمين المحور.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            ),
            quiz = listOf(
                LabQuizQuestion(
                    id = "q_l2_an_1",
                    questionEn = "For a rigid beam to stay in static rotational equilibrium around its pivot:",
                    questionAr = "لكي تبقى عارضة صلبة في حالة توازن دوراني ساكن حول محورها:",
                    optionsEn = listOf("Sum of torques Στ = 0", "Masses must be equal", "Friction must be zero"),
                    optionsAr = listOf("مجموع العزوم Στ = 0", "يجب تساوي الكتلتين", "يجب انعدام الاحتكاك"),
                    correctIndex = 0,
                    explanationEn = "Rotational equilibrium requires net torque Στ = 0 regardless of unequal masses.",
                    explanationAr = "شرط التوازن الدوراني هو انعدام مجموع العزوم (Στ = 0) حتى لو اختلفت الكتل.",
                    origin = ExerciseOrigin.GENERATED_PRACTICE
                )
            )
        )
    )

    /**
     * Parses a user-supplied JSON course pack extracted from official Algerian SM PDFs
     * and attaches it as an OFFICIAL_PDF_IMPORTED lesson/exercise pack.
     */
    fun parseImportedCourseJson(rawJson: String): LabLesson? {
        return try {
            val obj = JSONObject(rawJson)
            val title = obj.optString("title", "").trim()
            val summary = obj.optString("summary", "").trim()
            if (title.isEmpty() || summary.isEmpty()) return null
            LabLesson(
                id = "pdf_${System.currentTimeMillis()}",
                titleEn = title,
                titleAr = obj.optString("titleAr", title),
                summaryEn = summary,
                summaryAr = obj.optString("summaryAr", summary),
                sourceStatus = CurriculumSourceStatus.OFFICIAL_PDF_IMPORTED
            )
        } catch (_: Exception) {
            null
        }
    }
}
