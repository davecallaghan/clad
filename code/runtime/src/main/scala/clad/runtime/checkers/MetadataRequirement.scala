package clad.runtime.checkers

import scala.util.matching.Regex

enum MetadataRequirement:
  case KeyExists(key: String)
  case KeyEquals(key: String, value: String)
  case KeyMatches(key: String, pattern: Regex)
  case AllOf(requirements: Seq[MetadataRequirement])
  case AnyOf(requirements: Seq[MetadataRequirement])

  def isSatisfied(meta: Map[String, String]): Boolean = this match
    case KeyExists(k)     => meta.contains(k)
    case KeyEquals(k, v)  => meta.get(k).contains(v)
    case KeyMatches(k, p) => meta.get(k).exists(p.findFirstIn(_).isDefined)
    case AllOf(reqs)      => reqs.forall(_.isSatisfied(meta))
    case AnyOf(reqs)      => reqs.exists(_.isSatisfied(meta))
