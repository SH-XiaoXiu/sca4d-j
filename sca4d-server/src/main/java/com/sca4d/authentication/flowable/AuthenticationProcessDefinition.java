package com.sca4d.authentication.flowable;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 使用 Flowable BpmnModel API 声明并部署认证 Process。 */
@Component
@Slf4j
public class AuthenticationProcessDefinition implements ApplicationRunner {
    public static final String PROCESS_KEY = "authentication";
    public static final String PRIMARY_PROOF = "primary-proof";
    public static final String SECOND_FACTOR_PROOF = "second-factor-proof";
    public static final String COMPLETION = "completion";

    private static final String DEPLOYMENT_NAME = "sca4d-authentication-process-v1";
    private static final String RESOURCE_NAME = "authentication.bpmn20.xml";

    private final RepositoryService repositoryService;

    public AuthenticationProcessDefinition(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (repositoryService.createDeploymentQuery().deploymentName(DEPLOYMENT_NAME).count() > 0) {
            log.debug("认证过程定义已经部署 | deploymentName={}", DEPLOYMENT_NAME);
            return;
        }

        BpmnModel model = new BpmnModel();
        model.setTargetNamespace("https://jdev.ncraft.net");

        Process process = new Process();
        process.setId(PROCESS_KEY);
        process.setName("Authentication");
        process.setExecutable(true);
        model.addProcess(process);

        StartEvent processStart = new StartEvent();
        processStart.setId("authentication-start");
        process.addFlowElement(processStart);

        SubProcess lifecycle = new SubProcess();
        lifecycle.setId("authentication-lifecycle");
        lifecycle.setName("Authentication lifecycle");
        process.addFlowElement(lifecycle);
        process.addFlowElement(new SequenceFlow(processStart.getId(), lifecycle.getId()));

        StartEvent lifecycleStart = new StartEvent();
        lifecycleStart.setId("lifecycle-start");
        lifecycle.addFlowElement(lifecycleStart);

        UserTask primaryProof = new UserTask();
        primaryProof.setId(PRIMARY_PROOF);
        primaryProof.setName("Primary proof");
        lifecycle.addFlowElement(primaryProof);
        lifecycle.addFlowElement(new SequenceFlow(lifecycleStart.getId(), primaryProof.getId()));

        ExclusiveGateway secondFactorRequired = new ExclusiveGateway();
        secondFactorRequired.setId("second-factor-required");
        secondFactorRequired.setDefaultFlow("without-second-factor");
        lifecycle.addFlowElement(secondFactorRequired);
        lifecycle.addFlowElement(new SequenceFlow(primaryProof.getId(), secondFactorRequired.getId()));

        UserTask secondFactorProof = new UserTask();
        secondFactorProof.setId(SECOND_FACTOR_PROOF);
        secondFactorProof.setName("Second-factor proof");
        lifecycle.addFlowElement(secondFactorProof);

        SequenceFlow withSecondFactor = new SequenceFlow(secondFactorRequired.getId(), secondFactorProof.getId());
        withSecondFactor.setId("with-second-factor");
        withSecondFactor.setConditionExpression("${secondFactorRequired == true}");
        lifecycle.addFlowElement(withSecondFactor);

        UserTask completion = new UserTask();
        completion.setId(COMPLETION);
        completion.setName("Authentication completion");
        lifecycle.addFlowElement(completion);

        SequenceFlow withoutSecondFactor = new SequenceFlow(secondFactorRequired.getId(), completion.getId());
        withoutSecondFactor.setId("without-second-factor");
        lifecycle.addFlowElement(withoutSecondFactor);
        lifecycle.addFlowElement(new SequenceFlow(secondFactorProof.getId(), completion.getId()));

        EndEvent lifecycleEnd = new EndEvent();
        lifecycleEnd.setId("lifecycle-end");
        lifecycle.addFlowElement(lifecycleEnd);
        lifecycle.addFlowElement(new SequenceFlow(completion.getId(), lifecycleEnd.getId()));

        TimerEventDefinition expirationTimer = new TimerEventDefinition();
        expirationTimer.setTimeDuration("${authenticationTtl}");
        BoundaryEvent expiration = new BoundaryEvent();
        expiration.setId("authentication-expired");
        expiration.setAttachedToRef(lifecycle);
        expiration.setAttachedToRefId(lifecycle.getId());
        expiration.setCancelActivity(true);
        expiration.addEventDefinition(expirationTimer);
        process.addFlowElement(expiration);

        EndEvent completed = new EndEvent();
        completed.setId("authentication-completed");
        process.addFlowElement(completed);
        process.addFlowElement(new SequenceFlow(lifecycle.getId(), completed.getId()));

        EndEvent expired = new EndEvent();
        expired.setId("authentication-expired-end");
        process.addFlowElement(expired);
        process.addFlowElement(new SequenceFlow(expiration.getId(), expired.getId()));

        var deployment = repositoryService.createDeployment()
                .name(DEPLOYMENT_NAME)
                .addBpmnModel(RESOURCE_NAME, model)
                .deploy();
        log.info(
                "认证过程定义已部署 | deploymentId={} deploymentName={} processKey={}",
                deployment.getId(),
                DEPLOYMENT_NAME,
                PROCESS_KEY);
    }
}
