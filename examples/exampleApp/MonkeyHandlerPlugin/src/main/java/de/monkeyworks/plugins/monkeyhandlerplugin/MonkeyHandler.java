package de.monkeyworks.plugins.monkeyhandlerplugin;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class MonkeyHandler {

	@Execute
	public Object execute() throws ExecutionException {
		MessageDialog.openInformation(Display.getCurrent().getActiveShell(), Text.TITLE, Text.MESSAGE);
		return null;
	}
}
