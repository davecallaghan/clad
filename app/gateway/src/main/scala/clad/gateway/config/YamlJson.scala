package clad.gateway.config

import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** YAML (safe constructor only) as ujson, so every config file is handled as JSON afterwards. */
object YamlJson:
  def read(path: Path): ujson.Value = parse(Files.readString(path))

  def parse(text: String): ujson.Value = toJson(Yaml(SafeConstructor(LoaderOptions())).load[Object](text))

  private def toJson(v: Any): ujson.Value = v match
    case null                   => ujson.Null
    case m: java.util.Map[?, ?] => ujson.Obj.from(m.asScala.toSeq.map((k, x) => k.toString -> toJson(x)))
    case l: java.util.List[?]   => ujson.Arr.from(l.asScala.map(toJson))
    case s: String              => ujson.Str(s)
    case b: java.lang.Boolean   => ujson.Bool(b)
    case n: java.lang.Number    => ujson.Num(n.doubleValue)
    case d: java.util.Date      => ujson.Str(d.toInstant.toString.takeWhile(_ != 'T'))
    case other                  => ujson.Str(other.toString)
