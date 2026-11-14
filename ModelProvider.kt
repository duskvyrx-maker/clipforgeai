package ai.clipforge

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Engine(val title: String, val extensions: List<String>, val detail: String) {
    ONNX("ONNX Runtime", listOf("onnx"), "Loads ONNX models locally. Model-specific input/output pipelines are required."),
    LITERT("LiteRT / TensorFlow Lite", listOf("tflite"), "Loads TFLite models locally. Model-specific input/output pipelines are required."),
    GGUF("llama.cpp / GGUF", listOf("gguf"), "GGUF catalog and import are enabled; native llama.cpp runtime must be packaged for inference."),
    MEDIAPIPE("MediaPipe", listOf("task", "tflite"), "MediaPipe task models need a task-specific adapter and compatible task metadata."),
    API("Optional API provider", listOf(), "Optional remote provider extension; disabled in the on-device-first build.")
}

data class LocalModel(val name: String, val file: File, val engine: Engine, val bytes: Long)

interface ModelProvider {
    val engine: Engine
    fun validate(context: Context, file: File): String
}

/** A real ONNX Runtime session-open check. The app deliberately does not guess model tensor shapes. */
class OnnxProvider : ModelProvider {
    override val engine = Engine.ONNX
    override fun validate(context: Context, file: File): String {
        val env = OrtEnvironment.getEnvironment()
        env.createSession(file.absolutePath, OrtSession.SessionOptions()).use { session ->
            return "ONNX model opened successfully. Inputs: ${session.inputNames.joinToString()}. Outputs: ${session.outputNames.joinToString()}."
        }
    }
}

/** A real TensorFlow Lite interpreter-open check. */
class LiteRtProvider : ModelProvider {
    override val engine = Engine.LITERT
    override fun validate(context: Context, file: File): String {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes); buffer.rewind()
        Interpreter(buffer).use { interpreter ->
            return "TFLite model opened successfully. Inputs: ${interpreter.inputTensorCount}; outputs: ${interpreter.outputTensorCount}."
        }
    }
}

/**
 * Provider registry. Additional engines implement ModelProvider and are registered here.
 * GGUF and MediaPipe are identified/importable, but are not falsely reported as inference-ready
 * until their matching native/task runtime is bundled.
 */
object ProviderRegistry {
    val providers: List<ModelProvider> = listOf(OnnxProvider(), LiteRtProvider())
    fun forEngine(engine: Engine): ModelProvider? = providers.firstOrNull { it.engine == engine }
    fun supportText(engine: Engine): String = when(engine) {
        Engine.ONNX, Engine.LITERT -> "Runtime included"
        Engine.GGUF -> "Import supported; llama.cpp native runtime integration required"
        Engine.MEDIAPIPE -> "Import supported; task-specific MediaPipe integration required"
        Engine.API -> "Optional extension point; not used by local analysis"
    }
}
