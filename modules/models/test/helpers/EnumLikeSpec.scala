package helpers

import helpers.EnumLikeMapConverts._
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class EnumLikeSpec extends Specification {

  import EnumLikeSpec._

  "EnumLike" can {

    "be able to generate basicName correctly" in {
      Enum1.basicName mustEqual "EnumLikeSpec.Enum1"
      Inner.Enum2.basicName mustEqual "EnumLikeSpec.Inner.Enum2"
    }

    "be able to compare to other object" in {
      Enum1.A.in(Enum1.A) mustEqual true
      Enum1.A.in(Enum1.A, Enum1.B) mustEqual true
      Enum1.A.in(Enum1.B) mustEqual false
      Enum1.A.in(Enum1.C, Enum1.B) mustEqual false
    }

    "be able to convert to another EnumLike" in {
      val map1 = Map(
        Enum1.A -> 1,
        Enum1.B -> 2,
        Enum1.C -> 2
      )
      val map3 = Map(
        Enum3("A") -> 1,
        Enum3("B") -> 2,
        Enum3("C") -> 2
      )
      map1.keyToEnum[Enum3] mustEqual map3
    }
  }
}

object EnumLikeSpec {

  case class Enum1(self: String) extends AnyVal with EnumLike.Value

  object Enum1 extends EnumLike.Definition[Enum1] {

    val A       = Enum1("A")
    val B       = Enum1("B")
    val C       = Enum1("C")
    val Unknown = Enum1("Unknown")

    val values = Seq(A, B, C)

    implicit def self = this
  }

  object Inner {

    case class Enum2(self: String) extends AnyVal with EnumLike.Value

    object Enum2 extends EnumLike.Definition[Enum2] {

      val A       = Enum2("A")
      val B       = Enum2("B")
      val Unknown = Enum2("Unknown")

      val values = Seq(A, B)

      implicit def self = this
    }
  }

  case class Enum3(self: String) extends AnyVal with EnumLike.Value

  object Enum3 extends EnumLike.Definition[Enum3] {

    val Unknown = Enum3("Unknown")

    val values = Enum1.values.map(v => Enum3(v.self))

    implicit def self = this
  }
}