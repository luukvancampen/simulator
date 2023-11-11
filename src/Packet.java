import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Packet implements Cloneable {
    // Network.
    String data;
    double[] sourceCoordinate;
    HashSet<Node> received;

    // MAC.
    String macSource;
    String macDestination;

    // IP.
    String ipSource;
    String ipDestination;
    int timeToLive;
    Packet piggyBack;

    // Routing
    Set<OptionType> optionTypes;
    List<String> sourceRoute;

    // RouteRequest
    int identification;
    String targetAddress;

    // SourceRoute
    int segmentsLeft;
    int salvage;

    @Override
    public Packet clone() {
        try {
            return (Packet) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    // PacketType type;
    // double[] originCoordinate;
    // String originID;
    // String destination;
    // // this HashSet will contain all nodes that have received this particular
    // packet.
    // // This is required to make sure that the network does not send a particular
    // // packet to a node twice.
    // HashSet<Node> received;
    // UUID uuid = UUID.randomUUID();
    // Integer localBackoff;
    // Integer remoteBackoff;
    // Integer sequenceNumber;
    // String data;

    // @Override
    // public int hashCode() {
    // int result = 17;
    // result = 31 * result + this.uuid.hashCode();
    // return result;
    // }

    // public Packet(PacketType type, double[] originCoordinate, String originID,
    // String destination, HashSet<Node> received, Integer localBackoff, Integer
    // remoteBackoff, Integer sequenceNumber, String data) {
    // this.type = type;
    // this.originCoordinate = originCoordinate;
    // this.originID = originID;
    // this.destination = destination;
    // this.received = received;
    // this.localBackoff = localBackoff;
    // this.remoteBackoff = remoteBackoff;
    // this.sequenceNumber = sequenceNumber;
    // this.data = data;
    // }

    // public Packet(PacketType type, double[] originCoordinate, String originID,
    // String destination, HashSet<Node> received, Integer localBackoff, Integer
    // remoteBackoff, Integer sequenceNumber) {
    // this.type = type;
    // this.originCoordinate = originCoordinate;
    // this.originID = originID;
    // this.destination = destination;
    // this.received = received;
    // this.localBackoff = localBackoff;
    // this.remoteBackoff = remoteBackoff;
    // this.sequenceNumber = sequenceNumber;
    // }

}
