package org.linthaal.helpers.ncbi.eutils

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

/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

/** Copied from original ScalaXmlSupport because DTD issue
  */
import java.io.{ ByteArrayInputStream, InputStreamReader }
import javax.xml.parsers.{ SAXParser, SAXParserFactory }
import scala.collection.immutable
import scala.xml.{ NodeSeq, XML }
import org.apache.pekko.http.scaladsl.unmarshalling._
import org.apache.pekko.http.scaladsl.marshalling._
import org.apache.pekko.http.scaladsl.model._
import MediaTypes._

trait ScalaXmlSupport2 {
  implicit def defaultNodeSeqMarshaller: ToEntityMarshaller[NodeSeq] =
    Marshaller.oneOf(ScalaXmlSupport2.nodeSeqMediaTypes.map(nodeSeqMarshaller): _*)

  def nodeSeqMarshaller(mediaType: MediaType.NonBinary): ToEntityMarshaller[NodeSeq] =
    Marshaller.StringMarshaller.wrap(mediaType)(_.toString())

  implicit def defaultNodeSeqUnmarshaller: FromEntityUnmarshaller[NodeSeq] =
    nodeSeqUnmarshaller(ScalaXmlSupport2.nodeSeqContentTypeRanges: _*)

  def nodeSeqUnmarshaller(ranges: ContentTypeRange*): FromEntityUnmarshaller[NodeSeq] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ranges: _*).mapWithCharset { (bytes, charset) =>
      if (bytes.length > 0) {
        val reader = new InputStreamReader(new ByteArrayInputStream(bytes), charset.nioCharset)
        XML.withSAXParser(createSAXParser()).load(reader): NodeSeq // blocking call! Ideally we'd have a `loadToFuture`
      } else NodeSeq.Empty
    }

  /** Provides a SAXParser for the NodeSeqUnmarshaller to use. Override to
    * provide a custom SAXParser implementation. Will be called once for for
    * every request to be unmarshalled. The default implementation calls
    * `ScalaXmlSupport2.createSaferSAXParser`.
    */
  protected def createSAXParser(): SAXParser = ScalaXmlSupport2.createSaferSAXParser()
}
object ScalaXmlSupport2 extends ScalaXmlSupport2 {
  val nodeSeqMediaTypes: immutable.Seq[MediaType.NonBinary] = List(`text/xml`, `application/xml`, `text/html`, `application/xhtml+xml`)
  val nodeSeqContentTypeRanges: immutable.Seq[ContentTypeRange] = nodeSeqMediaTypes.map(ContentTypeRange(_))

  /** Creates a safer SAXParser. */
  def createSaferSAXParser(): SAXParser = {
    val factory = SAXParserFactory.newInstance()
    import javax.xml.XMLConstants

    // Constants manually imported from com.sun.org.apache.xerces.internal.impl.Constants
    // which isn't accessible any more when scalac option `-release 8` is used.
    val SAX_FEATURE_PREFIX = "http://xml.org/sax/features/"
    val EXTERNAL_GENERAL_ENTITIES_FEATURE = "external-general-entities"
    val EXTERNAL_PARAMETER_ENTITIES_FEATURE = "external-parameter-entities"
    val XERCES_FEATURE_PREFIX = "http://apache.org/xml/features/"
    val DISALLOW_DOCTYPE_DECL_FEATURE = "disallow-doctype-decl"

    factory.setFeature(SAX_FEATURE_PREFIX + EXTERNAL_GENERAL_ENTITIES_FEATURE, false)
    factory.setFeature(SAX_FEATURE_PREFIX + EXTERNAL_PARAMETER_ENTITIES_FEATURE, false)
    factory.setFeature(XERCES_FEATURE_PREFIX + DISALLOW_DOCTYPE_DECL_FEATURE, false) // CHANGED FROM ORIGINAL
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false) // CHANGED FROM ORIGINAL
    val parser = factory.newSAXParser()
    try {
      parser.setProperty("http://apache.org/xml/properties/locale", java.util.Locale.ROOT)
    } catch {
      case e: org.xml.sax.SAXNotRecognizedException => // property is not needed
    }
    parser
  }
}
