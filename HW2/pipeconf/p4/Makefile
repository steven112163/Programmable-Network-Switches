all: learning-bridge

learning-bridge: learning_bridge.p4
	p4c --target bmv2 \
		--arch v1model \
		--std p4-16 \
		-o ../src/main/resources/learning_bridge.json \
		--p4runtime-files ../src/main/resources/learning_bridge.p4info.txt \
		learning_bridge.p4