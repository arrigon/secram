/**
 * Copyright © 2013-2016 Swiss Federal Institute of Technology EPFL and Sophia Genetics SA
 * 
 * All rights reserved
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted 
 * provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of 
 * conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of 
 * conditions and the following disclaimer in the documentation and/or other materials provided 
 * with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used 
 * to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS 
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER 
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT 
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * PATENTS NOTICE: Sophia Genetics SA holds worldwide pending patent applications in relation with this 
 * software functionality. For more information and licensing conditions, you should contact Sophia Genetics SA 
 * at info@sophiagenetics.com. 
 */
package com.sg.secram.impl;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import com.sg.secram.structure.SecramBlock;
import com.sg.secram.structure.SecramCompressionHeaderFactory;
import com.sg.secram.structure.SecramContainer;
import com.sg.secram.structure.SecramContainerIO;
import com.sg.secram.util.Timings;

/**
 * Iterates the containers in a SECRAM file.
 * 
 * @author zhihuang
 * 
 */
public class SECRAMContainerIterator implements Iterator<SecramContainer> {
	private InputStream inputStream;
	private SECRAMSecurityFilter filter;
	private SecramContainer nextContainer = null;
	private boolean eof = false;

	/**
	 * Constructs the iterator over an input stream, with a security filter for decryption. 
	 */
	public SECRAMContainerIterator(InputStream inputStream,
			SECRAMSecurityFilter filter) {
		this.inputStream = inputStream;
		this.filter = filter;
	}

	private void readNextContainer() {
		try {
			long nanoStart = System.nanoTime();
			nextContainer = SecramContainerIO.readContainer(inputStream);
			Timings.IO += System.nanoTime() - nanoStart;

		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		if (null == nextContainer
				|| !filter.isContainerPermitted(nextContainer.absolutePosStart))
			eof = true;
		else {
			// initialize the block encryption for this container, and decrypt
			// the sensitive block
			try {
				filter.initContainerEM(nextContainer.containerSalt,
						nextContainer.containerID);
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
			SecramBlock sensitiveBlock = nextContainer.external
					.get(SecramCompressionHeaderFactory.SENSITIVE_FIELD_EXTERNAL_ID);
			long nanoStart = System.nanoTime();
			byte[] orginalBlock = filter.decryptBlock(
					sensitiveBlock.getRawContent(), nextContainer.containerID);
			Timings.decryption += System.nanoTime() - nanoStart;
			sensitiveBlock.setContent(orginalBlock, orginalBlock);
		}
	}

	@Override
	public boolean hasNext() {
		if (eof)
			return false;
		if (null == nextContainer)
			readNextContainer();
		return !eof;
	}

	@Override
	public SecramContainer next() {
		if (hasNext()) {
			SecramContainer result = nextContainer;
			nextContainer = null;
			return result;
		}
		return null;
	}

	public void close() {
		nextContainer = null;
		try {
			inputStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
