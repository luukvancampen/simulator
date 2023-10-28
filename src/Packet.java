import java.util.HashSet;

public class Packet implements Cloneable{
    String type;
    double[] originCoordinate;
    String originID;
    String destination;
    // this HashSet will contain all nodes that have received this particular packet.
    // This is required to make sure that the network does not send a particular
    // packet to a node twice.
    HashSet<Node> received;

    public Packet(String type, double[] originCoordinate, String originID, String destination, HashSet<Node> received) {
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
