# Programmable-Network-Switches HW1

## Description
The app aims to forward ARP and IPv4 packets.  
It requests these packets and forwards them according to the destination MAC.

## Run
1. Start onos  
    ```shell script
    $ cd ~/<directory-of-onos>
    $ ok clean
    ```
   
2. Start mininet  
    You can use any topology  
    ```shell script
    $ sudo mn --topo tree,2 --controller remote,ip=127.0.0.1
    ```

3. Build application  
    ```shell script
    $ cd learning-bridge
    $ mvn clean install -DskipTests
    ```

4. Install and activate app  
    ```shell script
    $ cd learning-bridge
    $ onos-app localhost install target/learning-bridge-1.0-SNAPSHOT.oar
    $ onos localhost app activate nctu.pncourse.bridge
    ```