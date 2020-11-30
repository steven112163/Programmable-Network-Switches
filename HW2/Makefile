BRIDGE_OAR = p4-learning-bridge/target/p4-learning-bridge-1.0-SNAPSHOT.oar
PIPE_OAR = pipeconf/target/pipeconf-1.0-SNAPSHOT.oar

default:
	$(error Please specify a make target (see README.md))

p4-build: pipeconf/p4/learning_bridge.p4
	$(info *** Building P4 program...)
	p4c --target bmv2 \
		--arch v1model \
		--std p4-16 \
		-o pipeconf/src/main/resources/learning_bridge.json \
		--p4runtime-files pipeconf/src/main/resources/learning_bridge.p4info.txt \
		pipeconf/p4/learning_bridge.p4

app-build:
	$(info *** Building ONOS apps...)
	cd p4-learning-bridge && mvn clean install -DskipTests
	cd pipeconf && mvn clean install -DskipTests

$(BRIDGE_OAR):
	$(error Missing p4-learning-bridge app binary, run 'make app-build' first)

$(PIPE_OAR):
	$(error Missing pipeconf app binary, run 'make app-build' first)

reload: $(BRIDGE_OAR) $(PIPE_OAR)
	$(info *** Reloading ONOS apps...)
	onos-app localhost reinstall! p4-learning-bridge/target/p4-learning-bridge-1.0-SNAPSHOT.oar
	onos-app localhost reinstall! pipeconf/target/pipeconf-1.0-SNAPSHOT.oar

load: $(BRIDGE_OAR) $(PIPE_OAR)
	$(info *** Loading ONOS apps...)
	onos-app localhost install! p4-learning-bridge/target/p4-learning-bridge-1.0-SNAPSHOT.oar
	onos-app localhost install! pipeconf/target/pipeconf-1.0-SNAPSHOT.oar

build: p4-build app-build