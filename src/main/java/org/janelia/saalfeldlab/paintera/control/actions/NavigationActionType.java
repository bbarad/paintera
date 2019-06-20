package org.janelia.saalfeldlab.paintera.control.actions;

import java.util.EnumSet;

public enum NavigationActionType implements ActionType
{
	Drag,
	Scroll,
	Zoom,
	Rotate;

	public static EnumSet<NavigationActionType> of(final NavigationActionType first, final NavigationActionType... rest)
	{
		return EnumSet.of(first, rest);
	}

	public static EnumSet<NavigationActionType> all()
	{
		return EnumSet.allOf(NavigationActionType.class);
	}

	public static EnumSet<NavigationActionType> none()
	{
		return EnumSet.noneOf(NavigationActionType.class);
	}
}
