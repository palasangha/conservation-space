package com.sirma.itt.seip.template.actions;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.sirma.itt.seip.instance.actions.Actions;

public class SetTemplateAsPrimaryRestServiceTest {

	@InjectMocks
	private SetTemplateAsPrimaryRestService setTemplateAsPrimaryRestService;

	@Mock
	private Actions actions;

	@Before
	public void beforeMethod() {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void should_Call_Set_As_Primary_Action() throws Exception {
		setTemplateAsPrimaryRestService.setAsPrimary(new SetTemplateAsPrimaryActionRequest());
		verify(actions).callAction(any(SetTemplateAsPrimaryActionRequest.class));
	}

}
