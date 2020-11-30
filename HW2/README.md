# Programmable-Network-Switches HW2

## Description
The p4-learning-bridge is from hw1.  
Pipeconf is the pipeline configuration app for p4-learning-bridge.  
This hw implements learning bridge on P4 switches.

## Run
1. Start onos  
    ```shell script
    $ cd ~/<directory-of-onos>
    $ ok clean
    ```
2. Compile files  
    ```shell script
    $ cd ~/<directory-of-this-hw>
    $ make build
    ```

3. Load apps  
    ```shell script
    $ make load
    ```

4. Start mininet 
    ```shell script
    $ sudo -E mn --custom $BMV2_MN_PY --switch onosbmv2,pipeconf=nctu.pncourse.pipeconf --controller remote,ip=127.0.0.1 --topo=tree,2
    ```