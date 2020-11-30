/* -*- P4_16 -*- */
#include <core.p4>
#include <v1model.p4>

/*************************************************************************
*********************** H E A D E R S  ***********************************
*************************************************************************/

typedef bit<9>  egressSpec_t;
typedef bit<48> macAddr_t;

const egressSpec_t CPU_PORT = 255;

// Ethernet header
header ethernet_t {
    macAddr_t dst_addr;
    macAddr_t src_addr;
    bit<16>   ether_type;
}

// Packet-in header
@controller_header("packet_in")
header packet_in_t {
    bit<9> ingress_port;
    bit<7> _padding;
}

// Packet-out header
@controller_header("packet_out")
header packet_out_t {
    bit<9> egress_port;
    bit<7> _padding;
}

struct metadata {
    /* empty */
}

struct headers_t {
    ethernet_t   ethernet;
    packet_in_t  packet_in;
    packet_out_t packet_out;
}

/*************************************************************************
*********************** P A R S E R  ***********************************
*************************************************************************/

parser MyParser(packet_in packet,
                out headers_t hdr,
                inout metadata meta,
                inout standard_metadata_t standard_metadata) {

    state start {
        transition select(standard_metadata.ingress_port) {
            CPU_PORT: parse_packet_out;
            default: parse_ethernet;
        }
    }

    state parse_packet_out {
        packet.extract(hdr.packet_out);
        transition parse_ethernet;
    }

    state parse_ethernet {
        packet.extract(hdr.ethernet);
        transition accept;
    }
}

/*************************************************************************
************   C H E C K S U M    V E R I F I C A T I O N   *************
*************************************************************************/

control MyVerifyChecksum(inout headers_t hdr, inout metadata meta) {
    apply {  }
}


/*************************************************************************
**************  I N G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control MyIngress(inout headers_t hdr,
                  inout metadata meta,
                  inout standard_metadata_t standard_metadata) {
    direct_counter(CounterType.packets) ether_counter;

    action drop() {
        mark_to_drop(standard_metadata);
    }

    action send_to_controller() {
        standard_metadata.egress_spec = CPU_PORT;
    }

    action set_egress_port(egressSpec_t port) {
        standard_metadata.egress_spec = port;
    }

    table ethernet_forward {
        key = {
            hdr.ethernet.dst_addr  : ternary;
            hdr.ethernet.ether_type: ternary;
        }
        actions = {
            drop;
            send_to_controller;
            set_egress_port;
            NoAction;
        }
        default_action = drop();
        size = 1024;
        counters = ether_counter;
    }
    
    apply {
        if (standard_metadata.ingress_port == CPU_PORT) {
            // Forward the packet in packet_out
            standard_metadata.egress_spec = hdr.packet_out.egress_port;
            hdr.packet_out.setInvalid();
        } else if (hdr.ethernet.isValid()) {
            ethernet_forward.apply();
        }
    }
}

/*************************************************************************
****************  E G R E S S   P R O C E S S I N G   *******************
*************************************************************************/

control MyEgress(inout headers_t hdr,
                 inout metadata meta,
                 inout standard_metadata_t standard_metadata) {
    apply {
        if (standard_metadata.egress_port == CPU_PORT) {
            hdr.packet_in.setValid();
            hdr.packet_in.ingress_port = standard_metadata.ingress_port;
        }
    }
}

/*************************************************************************
*************   C H E C K S U M    C O M P U T A T I O N   **************
*************************************************************************/

control MyComputeChecksum(inout headers_t hdr, inout metadata meta) {
     apply { }
}

/*************************************************************************
***********************  D E P A R S E R  *******************************
*************************************************************************/

control MyDeparser(packet_out packet, in headers_t hdr) {
    apply {
        packet.emit(hdr.packet_in);
        packet.emit(hdr.ethernet);
    }
}

/*************************************************************************
***********************  S W I T C H  *******************************
*************************************************************************/

V1Switch(
MyParser(),
MyVerifyChecksum(),
MyIngress(),
MyEgress(),
MyComputeChecksum(),
MyDeparser()
) main;
