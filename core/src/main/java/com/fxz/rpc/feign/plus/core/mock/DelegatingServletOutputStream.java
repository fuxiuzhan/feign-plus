package com.fxz.rpc.feign.plus.core.mock;

import org.springframework.util.Assert;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Delegating implementation of {@link ServletOutputStream}.
 *
 * <p>Used by {@link MockHttpServletResponse}; typically not directly
 * used for testing application controllers.
 *
 * @author Juergen Hoeller
 * @since 1.0.2
 * @see MockHttpServletResponse
 */
public class DelegatingServletOutputStream extends ServletOutputStream {

	private final OutputStream targetStream;


	/**
	 * Create a DelegatingServletOutputStream for the given target stream.
	 * @param targetStream the target stream (never {@code null})
	 */
	public DelegatingServletOutputStream(OutputStream targetStream) {
		Assert.notNull(targetStream, "Target OutputStream must not be null");
		this.targetStream = targetStream;
	}

	/**
	 * Return the underlying target stream (never {@code null}).
	 */
	public final OutputStream getTargetStream() {
		return this.targetStream;
	}


	@Override
	public void write(int b) throws IOException {
		this.targetStream.write(b);
	}

	@Override
	public void flush() throws IOException {
		super.flush();
		this.targetStream.flush();
	}

	@Override
	public void close() throws IOException {
		super.close();
		this.targetStream.close();
	}

	@Override
	public boolean isReady() {
		return true;
	}

	@Override
	public void setWriteListener(WriteListener writeListener) {
		throw new UnsupportedOperationException();
	}

}
