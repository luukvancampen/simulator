import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

// Based of of this RFC: https://datatracker.ietf.org/doc/rfc4728/.
public class Node implements Runnable {
    // Network.
    String id;
    double[] coordinate;
    double range;
    Network network;

    // Routing.
    private Map<String, List<RouteCacheEntry>> routeCache;
    private ArrayDeque<SendBufferEntry> sendBuffer;
    private Map<String, RouteRequestTableEntry> routeRequestTable;
    private List<GratituousReplyTableEntry> gratituousReplyTable;
    // TODO blacklist?
    private int routeRequestIdentificationCounter;

    Node(String id, double[] coordinate, double range, Network network) {
        this.id = id;
        this.coordinate = coordinate;
        this.range = range;
        this.network = network;

        routeCache = new HashMap<>();
        sendBuffer = new ArrayDeque<>();
        routeRequestTable = new HashMap<>();
        gratituousReplyTable = new ArrayList<>();
        routeRequestIdentificationCounter = 0;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(5);

                Optional<Packet> maybePacket = this.receive();

                if (maybePacket.isPresent()) {
                    System.out.println("PACKET RECEIVED");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Optional<Packet> receive() {
        Optional<Packet> maybePacket = network.receive(this);

        if (!maybePacket.isPresent()) {
            return Optional.empty();
        }

        Packet packet = maybePacket.get();

        if (packet.macDestination != id && packet.macDestination != "*") {
            return Optional.empty();
        }

        return receivePacket(maybePacket.get());
    }

    public void send(String receiver, String data) {
        Packet packet = new Packet();

        packet.ipSource = id;
        packet.ipDestination = receiver;

        packet.optionTypes = Set.of();
        packet.data = data;

        originatePacket(packet, false);
    }

    private void originatePacket(Packet packet, boolean piggyBackRouteRequest) {
        if (packet.ipDestination == "255.255.255.255" || packet.optionTypes.contains(OptionType.SourceRoute)) {
            network.send(packet);
        } else {
            Optional<List<String>> maybeRoute = findRoute(packet.ipDestination);

            if (maybeRoute.isPresent()) {
                List<String> sourceRoute = maybeRoute.get();
                sendWithSourceRoute(packet, sourceRoute);
            } else {
                routeDiscovery(packet, piggyBackRouteRequest);
            }
        }
    }

    private void sendWithSourceRoute(Packet packet, List<String> sourceRoute) {
        packet.sourceCoordinate = coordinate;
        packet.received = new HashSet<>();

        packet.macSource = id;

        if (!sourceRoute.isEmpty()) {
            Set<OptionType> optionTypes = new HashSet<>();
            optionTypes.addAll(packet.optionTypes);
            optionTypes.add(OptionType.SourceRoute);

            packet.macDestination = sourceRoute.get(0);

            packet.optionTypes = optionTypes;
            packet.sourceRoute = sourceRoute;
            packet.segmentsLeft = sourceRoute.size();
        } else {
            packet.macDestination = packet.ipDestination;
        }

        // TODO 8.3.0

        network.send(packet);
    }

    private Optional<Packet> receivePacket(Packet packet) {
        if (packet.ipSource == id) {
            return Optional.empty();
        }

        if (packet.optionTypes.contains(OptionType.RouteRequest)) {
            List<String> route = new ArrayList<>();
            route.add(packet.ipSource);
            route.addAll(packet.route);
            route.add(id);
            updateRoutingCache(route);

            if (packet.targetAddress == id) {
                route = new ArrayList<>();
                route.addAll(packet.route);
                route.add(packet.targetAddress);
                originateRouteReply(id, packet.ipSource, route, packet.identification);

                return Optional.empty();
            } else {

                if (packet.route.contains(id)) {
                    return Optional.empty();
                }

                // TODO maybe blacklist

                if (routeRequestInTable(packet)) {
                    return Optional.empty();
                }

                addRouteRequestEntry(packet);

                Optional<List<String>> maybeCachedRoute = findRoute(packet.targetAddress);

                if (maybeCachedRoute.isPresent()) {
                    List<String> cachedRoute = maybeCachedRoute.get();

                    boolean containsDuplicate = false;

                    if (cachedRoute.contains(packet.ipSource)) {
                        containsDuplicate = true;
                    }

                    for (String node : packet.route) {
                        if (cachedRoute.contains(node)) {
                            containsDuplicate = true;
                            break;
                        }
                    }

                    if (!containsDuplicate) {
                        route = new ArrayList<>();
                        route.addAll(packet.route);
                        route.add(id);
                        route.addAll(cachedRoute);
                        route.add(packet.targetAddress);

                        // TODO 8.2.5

                        originateRouteReply(id, packet.ipSource, route, packet.identification);

                        // TODO might need to propagate RouteRequest if other options present.

                        return Optional.empty();
                    }
                }

                packet.macSource = id;

                packet.timeToLive -= 1;

                packet.route.add(id);
            }
        }

        if (packet.optionTypes.contains(OptionType.RouteReply)) {
            List<String> route = new ArrayList<>();
            route.add(packet.ipDestination);
            route.addAll(packet.route);
            updateRoutingCache(route);
        }

        if (packet.optionTypes.contains(OptionType.RouteError)) {
            // TODO 8.3.5
        }

        if (packet.optionTypes.contains(OptionType.AcknowledgementRequest)) {
            // TODO 8.3.3
        }

        if (packet.optionTypes.contains(OptionType.Acknowledgement)) {
            List<String> link = List.of(packet.ipSource, packet.ipDestination);
            updateRoutingCache(link);

            // TODO 8.3.3
        }

        if (packet.optionTypes.contains(OptionType.SourceRoute)) {
            List<String> route = new ArrayList<>();
            route.add(packet.ipSource);
            route.addAll(packet.sourceRoute);
            route.add(packet.ipDestination);
            updateRoutingCache(route);

            // TODO 8.1.5
            // TODO automatic route shortening

            if (packet.segmentsLeft == 1) {
                if (packet.ipDestination != "255.255.255.255") {
                    packet.sourceCoordinate = coordinate;
                    packet.received = new HashSet<>();

                    packet.macSource = id;
                    packet.macDestination = packet.ipDestination;

                    packet.timeToLive -= 1;

                    packet.segmentsLeft -= 1;

                    // TODO 8.3
                }
            } else if (packet.segmentsLeft > 1) {
                int i = packet.sourceRoute.size() - packet.segmentsLeft + 1;

                if (packet.sourceRoute.get(i) != "255.255.255.255" && packet.ipDestination != "255.255.255.255") {
                    packet.sourceCoordinate = coordinate;
                    packet.received = new HashSet<>();

                    packet.macSource = id;
                    packet.macDestination = packet.sourceRoute.get(i);

                    packet.timeToLive -= 1;

                    packet.segmentsLeft -= 1;

                    // TODO 8.3
                }
            }
        }

        if (packet.ipDestination != id && !packet.isPiggyBack) {
            packet.sourceCoordinate = coordinate;
            packet.received = new HashSet<>();

            originatePacket(packet, false);
        } else {
            if (packet.data != null) {
                return Optional.of(packet);
            }
        }

        return Optional.empty();
    }

    private void originateRouteReply(String sourceAddress, String destinationAddress, List<String> route,
            int routeRequestIdentification) {
        Packet routeReplyPacket = new Packet();

        routeReplyPacket.sourceCoordinate = coordinate;
        routeReplyPacket.received = new HashSet<>();

        routeReplyPacket.macSource = id;

        if (route.size() >= 2) {
            routeReplyPacket.macDestination = route.get(route.size() - 2);
        } else {
            routeReplyPacket.macDestination = destinationAddress;
        }

        routeReplyPacket.ipSource = sourceAddress;
        routeReplyPacket.ipDestination = destinationAddress;
        routeReplyPacket.timeToLive = 255;

        routeReplyPacket.optionTypes = Set.of(OptionType.RouteReply);
        routeReplyPacket.route = route;
        routeReplyPacket.identification = routeRequestIdentification;

        // TODO sleep between 0 and BroadcastJitter. 8.2.4

        originatePacket(routeReplyPacket, true);
    }

    private void addRouteRequestEntry(Packet routeRequestPacket) {
        RouteRequestId routeRequestId = new RouteRequestId();
        routeRequestId.routeRequestIdentification = routeRequestPacket.identification;
        routeRequestId.targetAddress = routeRequestPacket.targetAddress;

        RouteRequestTableEntry routeRequestTableEntry = routeRequestTable.getOrDefault(id,
                new RouteRequestTableEntry());

        if (routeRequestTableEntry.routeRequests == null) {
            routeRequestTableEntry.consecutiveRequests = 0;
            routeRequestTableEntry.timeRemainingUntilNextRequest = 0;
            routeRequestTableEntry.routeRequests = new ArrayDeque<>();
        }

        routeRequestTableEntry.timeToLive = routeRequestPacket.timeToLive;
        routeRequestTableEntry.consecutiveRequests += 1;
        routeRequestTableEntry.routeRequests.add(routeRequestId);
    }

    private void updateRoutingCache(List<String> route) {
        String sourceAddress = route.get(0);

        for (int i = 1; i < route.size(); i++) {
            String destinationAddress = route.get(i);
            List<RouteCacheEntry> neighbours = routeCache.getOrDefault(sourceAddress, new ArrayList<>());

            for (int j = 0; j < neighbours.size(); j++) {
                if (neighbours.get(j).destinationAddress == destinationAddress) {
                    neighbours.remove(j);
                }
            }

            RouteCacheEntry routeCacheEntry = new RouteCacheEntry();
            routeCacheEntry.destinationAddress = destinationAddress;
            neighbours.add(routeCacheEntry);

            routeCache.put(sourceAddress, neighbours);

            sourceAddress = destinationAddress;
        }

        // System.out.println(id + " :: " + routeCache);

        checkSendBuffer();
    }

    private void checkSendBuffer() {
        for (SendBufferEntry sendBufferEntry : sendBuffer) {
            Optional<List<String>> maybeRoute = findRoute(sendBufferEntry.packet.ipDestination);

            if (maybeRoute.isPresent()) {
                List<String> sourceRoute = maybeRoute.get();
                sendWithSourceRoute(sendBufferEntry.packet, sourceRoute);

                sendBuffer.remove(sendBufferEntry);
            }
        }
    }

    private void routeDiscovery(Packet packet, boolean piggyBackRouteRequest) {
        Packet routeRequestPacket = new Packet();

        routeRequestPacket.sourceCoordinate = coordinate;
        routeRequestPacket.received = new HashSet<>();

        routeRequestPacket.macSource = id;
        routeRequestPacket.macDestination = "*";

        routeRequestPacket.ipSource = id;
        routeRequestPacket.ipDestination = "255.255.255.255";
        routeRequestPacket.timeToLive = 255;

        routeRequestPacket.optionTypes = Set.of(OptionType.RouteRequest);
        routeRequestPacket.route = new ArrayList<>();
        routeRequestPacket.identification = routeRequestIdentificationCounter;
        routeRequestPacket.targetAddress = packet.ipDestination;

        routeRequestIdentificationCounter = (routeRequestIdentificationCounter + 1) % Integer.MAX_VALUE;

        addRouteRequestEntry(routeRequestPacket);

        // TODO rate limit route requests

        if (!piggyBackRouteRequest) {
            SendBufferEntry sendBufferEntry = new SendBufferEntry();
            sendBufferEntry.packet = packet;
            sendBuffer.add(sendBufferEntry);
        } else {
            packet.macSource = routeRequestPacket.macSource;
            packet.macDestination = routeRequestPacket.macDestination;

            packet.isPiggyBack = true;

            routeRequestPacket.piggyBack = packet;
        }

        network.send(routeRequestPacket);
    }

    private boolean routeRequestInTable(Packet routeRequestPacket) {
        RouteRequestTableEntry routeRequestTableEntry = routeRequestTable.get(routeRequestPacket.ipSource);

        if (routeRequestTableEntry != null) {
            for (RouteRequestId id : routeRequestTableEntry.routeRequests) {
                if (id.routeRequestIdentification == routeRequestPacket.identification
                        && id.targetAddress == routeRequestPacket.targetAddress) {
                    return true;
                }
            }
        }

        return false;
    }

    private Optional<List<String>> findRoute(String destination) {
        Map<String, Integer> hops = new HashMap<>();
        Map<String, String> previous = new HashMap<>();

        List<String> queue = new ArrayList<>();

        for (String node : routeCache.keySet()) {
            if (node == id) {
                hops.put(node, 0);
            } else {
                hops.put(node, Integer.MAX_VALUE);
            }

            previous.put(node, null);
            queue.add(node);
        }

        while (!queue.isEmpty()) {
            String node = queue.get(0);
            int minHops = hops.get(node);
            int index = 0;

            for (int i = 1; i < queue.size(); i++) {
                String queuedNode = queue.get(i);
                int h = hops.get(queuedNode);

                if (h < minHops) {
                    minHops = h;
                    node = queuedNode;
                    index = i;
                }
            }

            queue.remove(index);

            for (RouteCacheEntry neighbour : routeCache.get(node)) {
                if (queue.contains(neighbour.destinationAddress)) {
                    int newHops = hops.get(node) + 1;

                    if (newHops < hops.get(neighbour.destinationAddress)) {
                        hops.put(neighbour.destinationAddress, newHops);
                        previous.put(neighbour.destinationAddress, node);

                        if (neighbour.destinationAddress == destination) {
                            queue.clear();
                        }
                    }
                }
            }
        }

        List<String> route = new ArrayList<>();

        String currentNode = destination;

        while (currentNode != id) {
            String prev = previous.get(currentNode);

            if (prev == null) {
                return Optional.empty();
            }

            currentNode = prev;
            route.add(0, currentNode);
        }

        route.remove(0);

        return Optional.of(route);
    }

    private final class RouteCacheEntry {
        private String destinationAddress;
        private int timeout;

        @Override
        public String toString() {
            return destinationAddress;
        }
    }

    private final class SendBufferEntry {
        private Packet packet;
        private int additionTime;
    }

    private final class RouteRequestTableEntry {
        private int timeToLive;
        private int consecutiveRequests;
        private int timeRemainingUntilNextRequest;
        private Queue<RouteRequestId> routeRequests;
    }

    private final class RouteRequestId {
        private int routeRequestIdentification;
        private String targetAddress;
    }

    private final class GratituousReplyTableEntry {
        private String targetAddress;
        private String sourceAddress;
        private int timeRemaining;
    }
}