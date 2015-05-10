import org.specs2.matcher.MatcherMacros
import org.specs2.mutable._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._

object Common {
  import reactivemongo.api._

  import scala.concurrent._
  import scala.concurrent.duration._

  implicit val ec = ExecutionContext.Implicits.global
  /*implicit val writer = DefaultBSONHandlers.DefaultBSONDocumentWriter
  implicit val reader = DefaultBSONHandlers.DefaultBSONDocumentReader
  implicit val handler = DefaultBSONHandlers.DefaultBSONReaderHandler*/

  val timeout = 5 seconds

  lazy val connection = new MongoDriver().connection(List("localhost:27017"))
  lazy val db = {
    val _db = connection("specs2-test-reactivemongo")
    Await.ready(_db.drop, timeout)
    _db
  }
}

case class Expeditor(name: String)
case class Item(name: String, description: String, occurrences: Int)
case class Package(
  expeditor: Expeditor,
  items: List[Item],
  price: Float)

class JsonBson extends Specification {
  import Common._
  import play.modules.reactivemongo.json.BSONFormats
  import play.modules.reactivemongo.json.ImplicitBSONHandlers._
  import reactivemongo.bson._

  sequential
  lazy val collection = db("somecollection_commonusecases")

  val pack = Package(
    Expeditor("Amazon"),
    List(Item("A Game of Thrones", "Book", 1)),
    20)

  "ReactiveMongo Plugin" should {
    "JsNumber(int) to BSONInteger" in {
      BSONFormats.toBSON(JsNumber(3)) must beLike { case JsSuccess(BSONInteger(value), _) => value must_== 3 }
    }
    "JsNumber(long) to BSONLong" in {
      BSONFormats.toBSON(JsNumber(Long.MaxValue)) must beLike {
        case JsSuccess(BSONLong(value), _) => value must_== Long.MaxValue
      }
    }
    "JsNumber(float) to BSONDouble" in {
      BSONFormats.toBSON(JsNumber(3.0)) must beLike { case JsSuccess(BSONDouble(value), _) => value must_== 3 }
    }

    "BSONInteger to JsNumber(int)" in {
      BSONFormats.toJSON(BSONInteger(3)) must beLike { case JsNumber(value) => value.isValidInt must_== true }
    }
    "BSONLong to JsNumber(long)" in {
      BSONFormats.toJSON(BSONLong(Long.MaxValue)) must beLike {
        case JsNumber(value) => value.isValidLong must_== true
      }
    }
    "BSONDouble to JsNumber(float)" in {
      BSONFormats.toJSON(BSONDouble(3.3434)) must beLike {
        case JsNumber(value) => value.ulp.isWhole must_== false
      }
    }
    "convert an empty json and give an empty bson doc" in {
      val json = Json.obj()
      val bson = BSONFormats.toBSON(json).get.asInstanceOf[BSONDocument]
      bson.isEmpty mustEqual true
      val json2 = BSONFormats.toJSON(bson)
      json2.as[JsObject].value.size mustEqual 0
    }
    "convert a simple json to bson and vice versa" in {
      val json = Json.obj("coucou" -> JsString("jack"))
      val bson = JsObjectWriter.write(json)
      val json2 = JsObjectReader.read(bson)
      json.toString mustEqual json2.toString
    }
    "convert a simple json array to bson and vice versa" in {
      val json = Json.arr(JsString("jack"), JsNumber(9.1))
      val bson = BSONFormats.toBSON(json).get.asInstanceOf[BSONArray]
      val json2 = BSONFormats.toJSON(bson)
      json.toString mustEqual json2.toString
    }
    "convert a json doc containing an array and vice versa" in {
      val json = Json.obj(
        "name" -> JsString("jack"),
        "contacts" -> Json.arr(
          Json.obj(
            "email" -> "jack@jack.com")))
      val bson = JsObjectWriter.write(json)
      val json2 = JsObjectReader.read(bson)
      json.toString mustEqual json2.toString
    }

    "format a jspath for mongo crud" in {
      import play.api.libs.functional.syntax._
      import play.modules.reactivemongo.json.Writers._

      case class Limit(low: Option[Int], high: Option[Int])
      case class App(limit: Option[Limit])

      val lowWriter = (__ \ "low").writeNullable[Int]
      val highWriter = (__ \ "high").writeNullable[Int]
      val limitWriter = (lowWriter and highWriter)(unlift(Limit.unapply))
      val appWriter = (__ \ "limit").writemongo[Limit](limitWriter)

      appWriter.writes(Limit(Some(1), None)) mustEqual
        Json.obj("limit.low" -> 1)
      appWriter.writes(Limit(Some(1), Some(2))) mustEqual
        Json.obj("limit.low" -> 1, "limit.high" -> 2)
      appWriter.writes(Limit(None, None)) mustEqual
        Json.obj()
    }
  }
}
