package software.amazon.datasync.locationnfs;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.datasync.model.*;
import software.amazon.cloudformation.exceptions.CfnServiceInternalErrorException;
import software.amazon.cloudformation.proxy.AmazonWebServicesClientProxy;
import software.amazon.cloudformation.proxy.HandlerErrorCode;
import software.amazon.cloudformation.proxy.Logger;
import software.amazon.cloudformation.proxy.OperationStatus;
import software.amazon.cloudformation.proxy.ProgressEvent;
import software.amazon.cloudformation.proxy.ResourceHandlerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UpdateHandlerTest {

    @Mock
    private AmazonWebServicesClientProxy proxy;

    @Mock
    private Logger logger;

    @BeforeEach
    public void setup() {
        proxy = mock(AmazonWebServicesClientProxy.class);
        logger = mock(Logger.class);
    }

    @Test
    public void handleRequest_TagUpdateSuccess() {
        final UpdateHandler handler = new UpdateHandler();

        when(proxy.injectCredentialsAndInvokeV2(any(), any())).thenAnswer(
                new Answer() {
                    final DescribeLocationNfsResponse describeLocationNfsResponse = buildDefaultResponse();
                    ListTagsForResourceResponse listTagsForResourceResponse = TagTestResources.buildDefaultTagsResponse();

                    public Object answer(InvocationOnMock invocation) {
                        Class type = invocation.getArgument(0).getClass();
                        if (ListTagsForResourceRequest.class.equals(type)) {
                            return listTagsForResourceResponse;
                        } else if (TagResourceRequest.class.equals(type)) {
                            // Very low-key mocking here, but we would like to verify that at least
                            // one tag-updating call is made or this test doesn't really test anything
                            listTagsForResourceResponse = TagTestResources.buildUpdatedTagsResponse();
                        }
                        return describeLocationNfsResponse;
                    }
                }
        );

        final ResourceModel model = buildDefaultModel();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .previousResourceState(model)
                .previousResourceTags(TagTranslator.translateTagsToMap(TagTestResources.defaultTags))
                .desiredResourceState(model)
                .desiredResourceTags(TagTranslator.translateTagsToMap(TagTestResources.updatedTags))
                .build();

        final ProgressEvent<ResourceModel, CallbackContext> response
                = handler.handleRequest(proxy, request, null, logger);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(response.getCallbackContext()).isNull();
        assertThat(response.getCallbackDelaySeconds()).isEqualTo(0);
        assertThat(response.getResourceModel().getTags()).isEqualTo(TagTestResources.updatedTags);
        assertThat(response.getResourceModels()).isNull();
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    public void handleRequest_FailureInternalException() {
        final UpdateHandler handler = new UpdateHandler();

        final ResourceModel model = ResourceModel.builder().build();

        final ResourceHandlerRequest<ResourceModel> request = ResourceHandlerRequest.<ResourceModel>builder()
                .desiredResourceState(model)
                .build();

        doThrow(InternalException.class)
                .when(proxy)
                .injectCredentialsAndInvokeV2(any(), any());

        assertThrows(CfnServiceInternalErrorException.class, () -> {
                    handler.handleRequest(proxy, request, null, logger);
        });
    }

    private static DescribeLocationNfsResponse buildDefaultResponse() {
        final String locationArn = "arn:aws:datasync:us-west-2:123456789012:location/loc-1234567890123456";
        final String locationUri = "nfs://10.0.1.0/";
        return DescribeLocationNfsResponse.builder()
                .locationArn(locationArn)
                .locationUri(locationUri)
                .build();
    }

    private static ResourceModel buildDefaultModel() {
        final String locationArn = "arn:aws:datasync:us-west-2:123456789012:location/loc-1234567890123456";
        return ResourceModel.builder()
                .locationArn(locationArn)
                .build();
    }
}
