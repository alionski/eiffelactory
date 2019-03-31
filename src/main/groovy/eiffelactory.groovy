import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.ToString
import groovy.transform.EqualsAndHashCode
import org.artifactory.fs.FileInfo
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPathFactory
import org.artifactory.search.Searches
import org.artifactory.search.aql.AqlResult
// This has to be places as a .jar under /lib
// TODO: import  and compile later via Maven?
import rabbitmqeiffelactory.*

/**
 * Represents an Eiffel link
 * @param type the link type.
 * @param target UUID of target, corresponds to meta.id of the target event.
 */
@ToString
@EqualsAndHashCode
class Link  {
    Type type
    UUID target

    Link(Type type, UUID target) {
        this.type = type
        this.target = target
    }

    // Legal link types
    enum Type {
        ARTIFACT,
        CAUSE,
        CONTEXT,
        FLOW_CONTEXT
    }
}

/**
 * Represents an Eiffel location
 * @param type The type of location.
 * @param uri The URI at which the artifact can be retrieved.
 */
@ToString
@EqualsAndHashCode
class Location {
    Type type
    String uri

    Location(Type type, String uri) {
        this.type = type
        this.uri = uri
    }

    // Legal location types
    enum Type {
        ARTIFACTORY,
        NEXUS,
        OTHER,
        PLAIN
    }
}

/**
 * Represents an Eiffel source
 * @param domainId Identifies the domain that produced an event.
 * @param host The hostname of the event sender.
 * @param name The name of the event sender.
 * @param serializer The identity of the serializer software used to construct the event, in purl format.
 * @param uri The URI of, related to or describing the event sender.
 */
@ToString
@EqualsAndHashCode
class Source {
    String domainId
    String host
    String name
    String serializer
    String uri
}

/**
 * Abstract class representing Eiffel meta data, all event meta classes must extend this class.
 * @param id The unique identity of the event, generated at event creation.
 * @param time The event creation timestamp, in milliseconds.
 * @param type The type of event.
 * @param version The version of the event type.
 * @param tags Tags or keywords associated with the events, for searchability purposes.
 * @param source The source of the event.
 */
@ToString
@EqualsAndHashCode
abstract class Meta {
    // Required default values
    UUID id = UUID.randomUUID()
    long time = System.currentTimeMillis()
    String type = this.class.getSimpleName().split("Meta")[0]

    // Required
    String version

    // Optional
    List<String> tags
    Source source

    Meta(Map optional = [:], String version) {
        this.type = type
        this.version = version
        this.tags = optional.tags
        this.source = optional.source
    }
}

interface EiffelEvent {
    Meta getMeta()
    List<Link> getLinks()
}

@ToString
@EqualsAndHashCode
class EiffelArtifactPublishedEvent implements EiffelEvent {
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
        super(optional, "3.0.0")
    }

    @Override
    String toString() {
        return type + super.toString()
    }
}

@ToString
@EqualsAndHashCode
class EiffelArtifactPublishedEventData {
    List<Location> locations

    EiffelArtifactPublishedEventData(List<Location> locations) {
        this.locations = locations
    }
}

/**
 * Class with helper methods for handling json data
 */
class JsonHelper {
    /**
     * Removes null values and empty lists from json data
     * @param json The json string to clean
     * @return a json string with all null values and empty lists removed
     */
    static String cleanJson(String json) {
        def jsonMap = new JsonSlurper().parseText(json)
        return JsonOutput.toJson(removeNullFromJsonObject(jsonMap))
    }

    /**
     * Recursively removes all null values and empty collections from objects representing json data
     * @param jsonObject The object representing the json data
     */
    private static def removeNullFromJsonObject(Object jsonObject) {
        if (jsonObject instanceof Map) {
            jsonObject.collectEntries {key, value ->
                if (value) {
                    [(key): removeNullFromJsonObject(value)]
                }
                else [:]
            }
        }
        else if (jsonObject instanceof Collection) {
            jsonObject.collect { removeNullFromJsonObject(it) }.findAll { it != null }
        }
        else {
            jsonObject
        }
    }
}


/**
 * Class for communicating with RabbitMQ
**/

class RabbitMQHelper {
    File logfile = new File("/tmp/rabbit.log")
    RecvMQ recv = new RecvMQ()
    SendMQ send = new SendMQ()

    def String createEiffelMessage() {
        List<Location> locs = new ArrayList<Location>()
        locs.add(new Location(Location.Type.ARTIFACTORY, "localhost"))
        List<Link> links = new ArrayList<Link>()
        links.add( new Link(Link.Type.ARTIFACT, UUID.randomUUID()))   
        String msg = new EiffelArtifactPublishedEvent(
            new EiffelArtifactPublishedEventMeta(),
            new EiffelArtifactPublishedEventData(locs),
            links)            
        return msg
    }

    def startSender() {
        new Thread(new Runnable(){
            void run() {
                while (true) {
                    String msg = JsonHelper.cleanJson(JsonOutput.toJson(createEiffelMessage()))
                    send.send(msg)
                    def now = new Date()
                    String timestamp = now.format("yyyyMMdd-HH:mm:ss.SSS", TimeZone.getTimeZone('UTC'))
                    logfile.append(timestamp + ": Sent\n")
                    Thread.sleep(5000)
                }
            }
        }).start()
    }

    def startReceiver() {
        recv.startReceiving()
    }
}

RabbitMQHelper rabbit = new RabbitMQHelper()
rabbit.startSender()
rabbit.startReceiver()

/**
 * Handle after create events.
 *
 * Closure parameters:
 * item (org.artifactory.fs.ItemInfo) - the original item being created.
 */

storage {
    afterCreate { item ->
        toFile(item)
    }
}

def getMetada(String repoKey) {
    File storageLog = new File("/tmp/storage_metadata.log")
    // <domain_query>.find(<criteria>).include(<fields>).sort(<order_and_fields>).offset(<offset_records>).limit(<num_records>)
    ((Searches) searches).aql(
        "items.find({\"repo\": \"" + repoKey + "\"}).include(\"name\", \"repo\", \"property.*\")") {
        AqlResult result ->
            result.each {
                storageLog.append(JsonOutput.prettyPrint(JsonOutput.toJson(result)))
            }
    }
}

def toFile(ItemInfo item) {
    File f = new File("/tmp/upload_metadata.log")
    def created = item.getCreated() as String
    def createdBy = item.getCreatedBy()
    def id = item.getId() as String
    def lastModified = item.getLastModified() as String
    def lastUpd = item.getLastUpdated() as String
    def modifiedBy = item.getModifiedBy() as String
    def name = item.getName()
    def relPath = item.getRelPath()
    def repoKey = item.getRepoKey()
    def repoPath = item.getRepoPath().toPath()
    def isFolder = item.isFolder() as String

    f.write("Name: " + name + "\n" +
            "Created: " + created + "\n" +
            "Created by: " + createdBy  + "\n" +
            "Id: " + id  + "\n" +
            "Last mod: " + lastModified  + "\n" +
            "last upd: " + lastUpd + "\n" +
            "Mod by: " + modifiedBy  + "\n" +
            "Rel path: " + relPath  + "\n" +
            "Repo key: " + repoKey  + "\n" +
            "Repo path: " + repoPath  + "\n" +
            "is folder: " + isFolder + "\n" )

    if (item instanceof FileInfo) {
        def fileInfo = item as FileInfo
        def sha256 = fileInfo.getSha2()
        def sha1 = fileInfo.getSha1()
        def md5 = fileInfo.getMd5()
        def mime = fileInfo.getMimeType()
        def age = fileInfo.getAge() as String
        def size = fileInfo.getSize()

        f.append("sha256: " + sha256 + "\n" +
                "sha1: " + sha1 + "\n" +
                "md5: " + md5 + "\n" +
                "Mime: " + mime + "\n" +
                "Age: "  + age + "\n" +
                "Size: " + size + "\n")
    }

    if (item instanceof FileLayoutInfo) {
        def fileLayout = item as FileLayoutInfo

        f.append("base revision: " + fileLayout.getBaseRevision() + "\n" +
                "Classifier : " + fileLayout.getClassifier() + "\n" +
                "Ext : " + fileLayout.getExt() + "\n")
    }

    getMetada(repoKey)
}

