package net.indiespot.struct.runtime;

public class UnsupportedCallsiteException extends IllegalStateException {

	private static final long serialVersionUID = 5538686706393104129L;

	public UnsupportedCallsiteException(String msg, String callsite, String target, String params, String args) {
		super("\r\n\t\tError:      " + msg + "\r\n" + //
				"\t\tCall site:  " + callsite + "\r\n" + //
				"\t\tTarget:     " + target + "\r\n" + //
				"\t\tParameters: " + params + "\r\n" + //
				"\t\tArguments:  " + args);
	}
}
