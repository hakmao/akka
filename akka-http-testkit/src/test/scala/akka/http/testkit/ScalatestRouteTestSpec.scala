/*
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.http.testkit

import scala.concurrent.duration._
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import akka.testkit.TestProbe
import akka.util.Timeout
import akka.pattern.ask
import akka.http.model.headers.RawHeader
import akka.http.server._
import akka.http.model._
import StatusCodes._
import HttpMethods._
import ScalaRoutingDSL._

class ScalatestRouteTestSpec extends FreeSpec with Matchers with ScalatestRouteTest {

  "The ScalatestRouteTest should support" - {

    "the most simple and direct route test" in {
      Get() ~> complete(HttpResponse()) ~> { rr ⇒ rr.awaitResult; rr.response } shouldEqual HttpResponse()
    }

    "a test using a directive and some checks" in {
      val pinkHeader = RawHeader("Fancy", "pink")
      Get() ~> addHeader(pinkHeader) ~> {
        respondWithHeader(pinkHeader) {
          complete("abc")
        }
      } ~> check {
        status shouldEqual OK
        entity shouldEqual HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }
    }

    "proper rejection collection" in {
      Post("/abc", "content") ~> {
        (get | put) {
          complete("naah")
        }
      } ~> check {
        rejections shouldEqual List(MethodRejection(GET), MethodRejection(PUT))
      }
    }

    "separation of route execution from checking" in {
      val pinkHeader = RawHeader("Fancy", "pink")

      case object Command
      val service = TestProbe()
      val handler = TestProbe()
      implicit def serviceRef = service.ref
      implicit val askTimeout: Timeout = 1.second

      val result =
        Get() ~> pinkHeader ~> {
          respondWithHeader(pinkHeader) {
            complete(handler.ref.ask(Command).mapTo[String])
          }
        } ~> runRoute

      handler.expectMsg(Command)
      handler.reply("abc")

      check {
        status shouldEqual OK
        entity shouldEqual HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abc")
        header("Fancy") shouldEqual Some(pinkHeader)
      }(result)
    }
  }

  // TODO: remove once RespondWithDirectives have been ported
  def respondWithHeader(responseHeader: HttpHeader): Directive0 =
    mapResponseHeaders(responseHeader +: _)
}
