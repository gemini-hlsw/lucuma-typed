import sbt._
import Keys._
import xerial.sbt.Sonatype.autoImport._
import org.typelevel.sbt.TypelevelSonatypePlugin
import org.typelevel.sbt.TypelevelVersioningPlugin

object LucumaTypedPlugin extends AutoPlugin {

  override def requires = TypelevelSonatypePlugin && TypelevelVersioningPlugin
  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    sonatypeProfileName := "edu.gemini",
  )
}
