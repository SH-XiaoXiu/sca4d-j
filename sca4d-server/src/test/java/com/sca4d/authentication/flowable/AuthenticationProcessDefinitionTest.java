package com.sca4d.authentication.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.DeploymentQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.DefaultApplicationArguments;

/** 验证认证 BpmnModel 的任务、第二因素分支、过期定时器和重复部署处理。 */
class AuthenticationProcessDefinitionTest {

    @Test
    void shouldSkipDeploymentWhenDefinitionAlreadyExists() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        DeploymentQuery query = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(query);
        when(query.deploymentName("sca4d-authentication-process-v1")).thenReturn(query);
        when(query.count()).thenReturn(1L);

        new AuthenticationProcessDefinition(repositoryService)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(repositoryService, never()).createDeployment();
    }

    @Test
    void shouldDeployTasksBranchesAndExpirationTimer() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        DeploymentQuery query = mock(DeploymentQuery.class);
        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(query);
        when(query.deploymentName("sca4d-authentication-process-v1")).thenReturn(query);
        when(query.count()).thenReturn(0L);
        when(repositoryService.createDeployment()).thenReturn(builder);
        when(builder.name("sca4d-authentication-process-v1")).thenReturn(builder);
        when(builder.addBpmnModel(anyString(), ArgumentMatchers.any(BpmnModel.class)))
                .thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("deployment-1");
        ArgumentCaptor<BpmnModel> modelCaptor = ArgumentCaptor.forClass(BpmnModel.class);

        new AuthenticationProcessDefinition(repositoryService)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(builder).addBpmnModel(ArgumentMatchers.eq("authentication.bpmn20.xml"), modelCaptor.capture());
        BpmnModel model = modelCaptor.getValue();
        assertThat(model.getTargetNamespace()).isEqualTo("https://jdev.ncraft.net");
        assertThat(model.getMainProcess().getId()).isEqualTo(AuthenticationProcessDefinition.PROCESS_KEY);

        SubProcess lifecycle = (SubProcess) model.getMainProcess().getFlowElement("authentication-lifecycle");
        assertThat(lifecycle.getFlowElement(AuthenticationProcessDefinition.PRIMARY_PROOF))
                .isInstanceOf(UserTask.class);
        assertThat(lifecycle.getFlowElement(AuthenticationProcessDefinition.SECOND_FACTOR_PROOF))
                .isInstanceOf(UserTask.class);
        assertThat(lifecycle.getFlowElement(AuthenticationProcessDefinition.COMPLETION))
                .isInstanceOf(UserTask.class);

        ExclusiveGateway gateway = (ExclusiveGateway) lifecycle.getFlowElement("second-factor-required");
        assertThat(gateway.getDefaultFlow()).isEqualTo("without-second-factor");
        SequenceFlow withSecondFactor = (SequenceFlow) lifecycle.getFlowElement("with-second-factor");
        assertThat(withSecondFactor.getConditionExpression()).isEqualTo("${secondFactorRequired == true}");
        assertThat(withSecondFactor.getTargetRef()).isEqualTo(AuthenticationProcessDefinition.SECOND_FACTOR_PROOF);
        SequenceFlow withoutSecondFactor = (SequenceFlow) lifecycle.getFlowElement("without-second-factor");
        assertThat(withoutSecondFactor.getTargetRef()).isEqualTo(AuthenticationProcessDefinition.COMPLETION);

        BoundaryEvent expiration = (BoundaryEvent) model.getMainProcess().getFlowElement("authentication-expired");
        assertThat(expiration.getAttachedToRefId()).isEqualTo(lifecycle.getId());
        assertThat(expiration.isCancelActivity()).isTrue();
        assertThat(expiration.getEventDefinitions()).singleElement().isInstanceOf(TimerEventDefinition.class);
        TimerEventDefinition timer = (TimerEventDefinition) expiration.getEventDefinitions().getFirst();
        assertThat(timer.getTimeDuration()).isEqualTo("${authenticationTtl}");
    }
}
