import java.util.HashSet;
import java.util.UUID;

public class Packet implements Cloneable{
    PacketType type;
    double[] originCoordinate;
    String originID;
    String destination;
    // this HashSet will contain all nodes that have received this particular packet.
    // This is required to make sure that the network does not send a particular
    // packet to a node twice.
    HashSet<Node> received;
    UUID uuid = UUID.randomUUID();

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + this.uuid.hashCode();
        return result;
    }

    public Packet(PacketType type, double[] originCoordinate, String originID, String destination, HashSet<Node> received) {
        this.type = type;
        this.originCoordinate = originCoordinate;
        this.originID = originID;
        this.destination = destination;
        this.received = received;
    }


    @Override
    public Packet clone() {
        try {
            return (Packet) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
