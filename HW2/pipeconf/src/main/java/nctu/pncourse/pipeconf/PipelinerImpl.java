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
package nctu.pncourse.pipeconf;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.NextGroup;
import org.onosproject.net.behaviour.Pipeliner;
import org.onosproject.net.behaviour.PipelinerContext;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.FilteringObjective;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.NextObjective;
import org.onosproject.net.flowobjective.ObjectiveError;
import org.onosproject.net.pi.model.PiActionId;
import org.onosproject.net.pi.model.PiTableId;
import org.onosproject.net.pi.runtime.PiAction;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;

import static org.onosproject.net.flow.instructions.Instruction.Type.OUTPUT;
import static org.onosproject.net.flow.criteria.Criterion.Type.ETH_TYPE;
import static org.slf4j.LoggerFactory.getLogger;

public class PipelinerImpl extends AbstractHandlerBehaviour implements Pipeliner {

    // Tables
    private static final PiTableId TABLE_ETHERNET_EXACT = PiTableId.of("MyIngress.ethernet_exact");
    private static final PiTableId TABLE_CONTROL_MESSAGE = PiTableId.of("MyIngress.control_message");

    // Actions
    private static final PiActionId ACT_ID_SEND_TO_CONTROLLER = PiActionId.of("MyIngress.send_to_controller");

    private final Logger log = getLogger(getClass());

    private FlowRuleService flowRuleService;
    private DeviceId deviceId;


    @Override
    public void init(DeviceId deviceId, PipelinerContext context) {
        this.deviceId = deviceId;
        this.flowRuleService = context.directory().get(FlowRuleService.class);
    }

    @Override
    public void filter(FilteringObjective obj) {
        obj.context().ifPresent(c -> c.onError(obj, ObjectiveError.UNSUPPORTED));
    }

    @Override
    public void forward(ForwardingObjective obj) {
        if (obj.treatment() == null) {
            obj.context().ifPresent(c -> c.onError(obj, ObjectiveError.UNSUPPORTED));
        }

        // Whether this objective specifies an OUTPUT:CONTROLLER instruction.
        final boolean hasCloneToCpuAction = obj.treatment()
                .allInstructions().stream()
                .filter(i -> i.type().equals(OUTPUT))
                .map(i -> (Instructions.OutputInstruction) i)
                .anyMatch(i -> i.port().equals(PortNumber.CONTROLLER));

        // Whether this objective has ETH_TYPE matching.
        final boolean hasEthTypeSelection = obj.selector()
                .criteria().stream()
                .anyMatch(i -> i.type().equals(ETH_TYPE));

        final FlowRule.Builder ruleBuilder;
        if (hasCloneToCpuAction && hasEthTypeSelection) {
            // The objective is a control message required by ONOS.
            final PiAction cloneToCpuAction = PiAction.builder()
                    .withId(ACT_ID_SEND_TO_CONTROLLER)
                    .build();
            ruleBuilder = DefaultFlowRule.builder()
                    .forTable(TABLE_CONTROL_MESSAGE)
                    .forDevice(deviceId)
                    .withSelector(obj.selector())
                    .fromApp(obj.appId())
                    .withPriority(obj.priority())
                    .withTreatment(DefaultTrafficTreatment.builder()
                            .piTableAction(cloneToCpuAction).build());
        } else {
            // The objective is from my app.
            ruleBuilder = DefaultFlowRule.builder()
                    .forTable(TABLE_ETHERNET_EXACT)
                    .forDevice(deviceId)
                    .withSelector(obj.selector())
                    .fromApp(obj.appId())
                    .withPriority(obj.priority())
                    .withTreatment(obj.treatment());
        }

        if (obj.permanent()) {
            ruleBuilder.makePermanent();
        } else {
            ruleBuilder.makeTemporary(obj.timeout());
        }

        switch (obj.op()) {
            case ADD:
                flowRuleService.applyFlowRules(ruleBuilder.build());
                break;
            case REMOVE:
                flowRuleService.removeFlowRules(ruleBuilder.build());
                break;
            default:
                log.warn("Unknown operation {}", obj.op());
        }

        obj.context().ifPresent(c -> c.onSuccess(obj));
    }

    @Override
    public void next(NextObjective obj) {
        obj.context().ifPresent(c -> c.onError(obj, ObjectiveError.UNSUPPORTED));
    }

    @Override
    public List<String> getNextMappings(NextGroup nextGroup) {
        // We do not use nextObjectives or groups.
        return Collections.emptyList();
    }
}
