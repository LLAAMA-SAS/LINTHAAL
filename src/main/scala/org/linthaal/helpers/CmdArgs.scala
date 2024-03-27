package org.linthaal.helpers

import org.linthaal.helpers.CmdArgs
import scopt.OParser

import java.io.File
import java.nio.file.Path

final case class CmdArgs(apiKeys: Map[String, String] = Map.empty, apiKeysDir: File = new File("."), cacheDir: File = new File("."))

object CmdArgs {

  private val builder = OParser.builder[CmdArgs]

  val parser: OParser[Unit, CmdArgs] = {
    import builder.*

    OParser.sequence(
      programName("linthaal"),
      head("linthaal", "1.3.0"),
      help("help").text("displays available arguments"),
      opt[String]("ncbi_api_key").action((x, c) => c.copy(apiKeys = c.apiKeys + ("ncbi.api_key" -> x))).text("API key for NCBI"),
      opt[String]("openai_api_key").action((x, c) => c.copy(apiKeys = c.apiKeys + ("openai.api_key" -> x))).text("API key for OpenAI"),
      opt[String]("huggingface_api_key").action((x, c) => c.copy(apiKeys = c.apiKeys + ("huggingface.api_key" -> x))).text("API key for Hugging Face"),
      opt[File]("api_keys_dir")
        .action((x, c) => c.copy(apiKeysDir = x))
        .withFallback(() => new File("/home/linthaal/keys"))
        .text("path to directory containing .api_key files")
        .valueName("<file>"),
      opt[File]("cache_dir")
        .action((x, c) => c.copy(cacheDir = x))
        .withFallback(() => Path.of(System.getProperty("user.dir")).resolve("cache").toFile)
        .text("path to directory containing cached files")
        .valueName("<file>"))
  }
}
