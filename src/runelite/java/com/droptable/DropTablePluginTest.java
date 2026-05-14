package com.droptable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class DropTablePluginTest
{
	public static void main(String[] args) throws Exception
	{
		haltOnInterrupt();
		ExternalPluginManager.loadBuiltin(DropTablePlugin.class);
		RuneLite.main(args);
	}

	private static void haltOnInterrupt()
	{
		try
		{
			Class<?> signalClass = Class.forName("sun.misc.Signal");
			Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
			Constructor<?> signalConstructor = signalClass.getConstructor(String.class);
			Method handle = signalClass.getMethod("handle", signalClass, handlerClass);
			Object signal = signalConstructor.newInstance("INT");
			Object handler = Proxy.newProxyInstance(
				DropTablePluginTest.class.getClassLoader(),
				new Class<?>[] {handlerClass},
				(proxy, method, args) -> {
					Runtime.getRuntime().halt(0);
					return null;
				});
			handle.invoke(null, signal, handler);
		}
		catch (ReflectiveOperationException | RuntimeException ignored)
		{
			// Ctrl-C fallback is best-effort for the local RuneLite test launcher.
		}
	}
}
