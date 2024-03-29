package com.sirma.sep.content.idoc.extensions.widgets.objectdata;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import com.sirma.sep.content.idoc.extensions.widgets.objectdata.ObjectDataWidgetVersionHandler;
import com.sirma.sep.content.idoc.extensions.widgets.utils.WidgetMock;
import com.sirma.sep.content.idoc.nodes.widgets.objectdata.ObjectDataWidget;

/**
 * Test for {@link ObjectDataWidgetVersionHandler}.
 *
 * @author A. Kunchev
 */
public class ObjectDataWidgetVersionHandlerTest {

	private ObjectDataWidgetVersionHandler handler;

	@Before
	public void setup() {
		handler = new ObjectDataWidgetVersionHandler();
	}

	@Test
	public void accept_incorrectType_false() {
		boolean result = handler.accept(new WidgetMock());
		assertFalse(result);
	}

	@Test
	public void accept_correctType_true() {
		boolean result = handler.accept(mock(ObjectDataWidget.class));
		assertTrue(result);
	}

}
