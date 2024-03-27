package org.linthaal.helpers

import org.linthaal.helpers.CmdArgs
import scopt.OParser

import java.io.File
import java.nio.file.Path

/** This program is free software: you can redistribute it and/or modify it
  * under the terms of the GNU General Public License as published by the Free
  * Software Foundation, either version 3 of the License, or (at your option)
  * any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT
  * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
  * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
  * more details.
  *
  * You should have received a copy of the GNU General Public License along with
  * this program. If not, see <http://www.gnu.org/licenses/>.
  */
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
