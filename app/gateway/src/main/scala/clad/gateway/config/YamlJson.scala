package clad.gateway.config

import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.{AbstractConstruct, SafeConstructor}
import org.yaml.snakeyaml.nodes.{Node, ScalarNode, Tag}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** YAML (safe constructor only) as ujson, so every config file is handled as JSON afterwards. */
object YamlJson:
  /** SafeConstructor that keeps a timestamp or date scalar as written: SnakeYAML would turn it into a java.util.Date,
    * which cannot tell a date from a midnight instant, and an unquoted attested_at must stay a full instant. */
  private final class Constructor(options: LoaderOptions) extends SafeConstructor(options):
    yamlConstructors.put(Tag.TIMESTAMP, new AbstractConstruct:
      def construct(node: Node): Object = node.asInstanceOf[ScalarNode].getValue)

  def read(path: Path): ujson.Value = parse(Files.readString(path))

  def parse(text: String): ujson.Value = toJson(Yaml(Constructor(LoaderOptions())).load[Object](text))

  private def toJson(v: Any): ujson.Value = v match
    case null                   => ujson.Null
    case m: java.util.Map[?, ?] => ujson.Obj.from(m.asScala.toSeq.map((k, x) => k.toString -> toJson(x)))
    case l: java.util.List[?]   => ujson.Arr.from(l.asScala.map(toJson))
    case s: String              => ujson.Str(s)
    case b: java.lang.Boolean   => ujson.Bool(b)
    case n: java.lang.Number    => ujson.Num(n.doubleValue)
    case other                  => ujson.Str(other.toString)
