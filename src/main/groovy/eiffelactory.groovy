import groovy.transform.ToString

/**
 * Represents an Eiffel link
 * @param type the link type
 * @param target UUID of target, corresponds to meta.id of the target event
 */
@ToString
class Link  {
    String type
    UUID target

    Link(String type, UUID target) {
        this.type = type
        this.target = target
    }
}

/**
 * Represents an Eiffel location
 * @param type The type of location.
 * @param uri The URI at which the artifact can be retrieved
 */
@ToString
class Location {
    Type type
    String uri

    Location(Type type, String uri) {
        this.type = type
        this.uri = uri
    }

    enum Type {
        ARTIFACTORY,
        NEXUS,
        OTHER,
        PLAIN
    }
}

@ToString
class Source {
    String domainId
    String host
    String name
    String serializer
    String uri
}

@ToString
class Meta {
    UUID id = UUID.randomUUID()
    long time = System.currentTimeMillis()
    String type
    String version
    List<String> tags
    Source source

    Meta(Map optional = [:], String type, String version) {
        this.type = type
        this.version = version
        this.tags = optional.tags
        this.source = optional.source
    }
}

@ToString
class EiffelArtifactPublishedEvent {
    EiffelArtifactPublishedEventMeta meta
    EiffelArtifactPublishedEventData data
    List<Link> links

    EiffelArtifactPublishedEvent(EiffelArtifactPublishedEventMeta meta,
                                 EiffelArtifactPublishedEventData data,
                                 List<Link> links) {
        this.meta = meta
        this.data = data
        this.links = links
    }
}

class EiffelArtifactPublishedEventMeta extends Meta {

    EiffelArtifactPublishedEventMeta(Map optional = [:]) {
        super(optional, "EiffelArtifactPublishedEvent", "3.0.0")
    }

    @Override
    String toString() {
        return "EiffelArtifactPublishedEvent" + super.toString()
    }
}

@ToString
class EiffelArtifactPublishedEventData {
    List<Location> locations

    EiffelArtifactPublishedEventData(List<Location> locations) {
        this.locations = locations
    }
}
/**
 * Handle after create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */
import org.artifactory.fs.ItemInfo

storage {
    afterCreate { item ->
        toFile(item)
    }
}

def toFile(ItemInfo item) {
    File f = new File("/tmp/test1.log")
    def name = item.getName()
    f.write name
}

