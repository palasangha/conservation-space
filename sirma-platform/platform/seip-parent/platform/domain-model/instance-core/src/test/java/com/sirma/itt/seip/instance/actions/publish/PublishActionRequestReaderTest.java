package com.sirma.itt.seip.instance.actions.publish;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.io.InputStream;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.sirma.itt.seip.rest.utils.request.RequestInfo;
import com.sirma.itt.seip.rest.utils.request.params.RequestParams;

/**
 * Test for {@link PublishActionRequestReader}
 *
 * @author BBonev
 */
public class PublishActionRequestReaderTest {

	@InjectMocks
	private PublishActionRequestReader reader;

	@Mock
	private RequestInfo info;

	@Mock
	private UriInfo uriInfo;

	@Before
	public void beforeMethod() {
		MockitoAnnotations.initMocks(this);
		when(info.getUriInfo()).thenReturn(uriInfo);
		when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
		uriInfo.getPathParameters().add(RequestParams.KEY_ID, "emf:instanceId");
	}

	@Test
	public void parseRequest() throws Exception {
		PublishActionRequest request = reader.readFrom(null, null, null, null, null, getValidRequestData());
		assertNotNull(request);
		assertEquals("emf:instanceId", request.getTargetId());
		assertEquals("publish", request.getUserOperation());
	}

	private static InputStream getValidRequestData() {
		return PublishActionRequestReaderTest.class.getClassLoader()
				.getResourceAsStream(PublishActionRequestReaderTest.class.getPackage().getName().replace('.', '/')
						+ "/publish-request.json");
	}
}
