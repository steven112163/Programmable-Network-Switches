/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.pncourse.bridge;

import com.google.common.collect.ImmutableSet;
import org.onlab.packet.Ethernet;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.packet.PacketPriority;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/** Libraries for hw */
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;

import org.onosproject.net.PortNumber;
import org.onosproject.net.Path;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;

import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.InboundPacket;

import org.onosproject.net.topology.TopologyService;

import org.onosproject.net.host.HostService;

import java.util.Set;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true,
        service = {SomeInterface.class},
        property = {
                "someProperty=Some Default String Value",
        })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Some configurable property.
     */
    private String someProperty;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;

    /**
     * Services for hw
     */
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;

    // App ID
    private ApplicationId app_id;

    // Packet processor
    private BridgeProcessor processor = new BridgeProcessor();

    // Default flow rule parameters
    private static final int DEFAULT_TIMEOUT = 10;
    private static final int DEFAULT_PRIORITY = 50000;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());
        app_id = coreService.registerApplication("nctu.pncourse.bridge");
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestsPackets();
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        flowRuleService.removeFlowRulesById(app_id);
        packetService.removeProcessor(processor);
        processor = null;
        cancelPackets();
        log.info("Stopped");
    }

    /**
     * Request Packet-in via PacketService
     */
    private void requestsPackets() {
        // Get all IPv4 packets
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, app_id);

        // Get all ARP packets
        selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, app_id);
    }

    /**
     * Cancel requested Packet-in via packet service
     */
    private void cancelPackets() {
        // Cancel all IPV4 packets
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, app_id);

        // Cancel all ARP packets
        selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, app_id);
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    private class BridgeProcessor implements PacketProcessor {
        /**
         * Process the packets
         *
         * @param context content of the incoming message
         */
        @Override
        public void process(PacketContext context) {
            // Stop processing if the packet is handled
            if (context.isHandled())
                return;

            InboundPacket pkt = context.inPacket();
            Ethernet eth_pkt = pkt.parsed();

            // Don't process null packet
            if (eth_pkt == null)
                return;

            // Don't process control packet
            if (is_control_packet(eth_pkt))
                return;

            // Get destination host ID from destination MAC
            // Use destination host ID to get location information about host
            HostId dst_id = HostId.hostId(eth_pkt.getDestinationMAC());
            Host dst = hostService.getHost(dst_id);

            // Don't process the packet if it's destination MAC is LLDP
            if (dst_id.mac().isLldp())
                return;

            // Forward to the destination if packet is on the edge switch
            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                // Install rule and packet-out only if destination port is different from where the packet came
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    log.info("Packet reached destination switch {}", dst.location().deviceId());
                    install_rule(context, dst.location().port());
                }
                return;
            }

            // Find path to the destination
            Set<Path> paths;
            paths = topologyService.getPaths(topologyService.currentTopology(),
                    pkt.receivedFrom().deviceId(), dst.location().deviceId());

            // Flood if there is no path
            if (paths.isEmpty()) {
                log.warn("Flood the packet");
                flood(context);
                return;
            }

            // Pick a path that doesn't lead back to the where the packet came from
            Path path = pick_forward_path_if_possible(paths, pkt.receivedFrom().port());

            // Flood if it doesn't know how to get to the destination
            if (path == null) {
                log.warn("Doesn't know how to forward src: {}, dst: {} from switch {}", eth_pkt.getSourceMAC(),
                        eth_pkt.getDestinationMAC(), pkt.receivedFrom());
                flood(context);
                return;
            }

            // Install rule and packet-out
            install_rule(context, path.src().port());
        }

        /**
         * Check whether it's control packet
         *
         * @param eth_pkt Ethernet packet
         * @return boolean
         */
        private boolean is_control_packet(Ethernet eth_pkt) {
            short type = eth_pkt.getEtherType();
            return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
        }

        /**
         * Output packet from the port
         *
         * @param context content of the incoming message
         * @param port    output port number
         */
        private void packet_out(PacketContext context, PortNumber port) {
            context.treatmentBuilder().setOutput(port);
            context.send();
        }

        /**
         * Flood the packet
         *
         * @param context content of the incoming packet
         */
        private void flood(PacketContext context) {
            if (topologyService.isBroadcastPoint(topologyService.currentTopology(), context.inPacket().receivedFrom()))
                packet_out(context, PortNumber.FLOOD);
            else
                context.block();
        }

        /**
         * Pick a path that doesn't lead back to where the packet came from
         *
         * @param paths all paths that lead to the destination
         * @param port  the port where packet came from
         */
        private Path pick_forward_path_if_possible(Set<Path> paths, PortNumber port) {
            for (Path path : paths) {
                if (!path.src().port().equals(port))
                    return path;
            }
            return null;
        }

        /**
         * Install flow rules on a switch and packet-out
         *
         * @param context content of the incoming packet
         * @param port    output port to be defined in the flow rule
         */
        private void install_rule(PacketContext context, PortNumber port) {
            InboundPacket pkt = context.inPacket();
            Ethernet eth_pkt = pkt.parsed();

            // Setup match fields
            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthDst(eth_pkt.getDestinationMAC())
                    .build();

            // Setup action fields
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(port)
                    .build();

            // Setup flow-mod object
            ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                    .withSelector(selector)
                    .withTreatment(treatment)
                    .withPriority(DEFAULT_PRIORITY)
                    .withFlag(ForwardingObjective.Flag.VERSATILE)
                    .fromApp(app_id)
                    .makeTemporary(DEFAULT_TIMEOUT)
                    .add();

            // Forward flow-mod object
            flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(), forwardingObjective);

            // Packet-out
            packet_out(context, port);
        }
    }

}
