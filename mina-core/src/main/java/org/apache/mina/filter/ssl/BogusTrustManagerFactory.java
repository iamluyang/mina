/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.mina.filter.ssl;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

/**
 * 一个虚假的信任关联工厂，相信一切
 *
 * Bogus {@link javax.net.ssl.TrustManagerFactory} which creates
 * {@link javax.net.ssl.X509TrustManager} trusting everything.
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
public class BogusTrustManagerFactory extends TrustManagerFactory {

	// X509TrustManager管理器
	// 此接口的实例管理哪些 X509 证书可用于验证安全套接字的远程端。
	// 决策可能基于受信任的证书颁发机构、证书撤销列表、在线状态检查或其他方式。
	private static final X509TrustManager X509 = new X509TrustManager() {
		/**
		 * {@inheritDoc}
		 */
		@Override
		public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
			// Do nothing
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
			// Do nothing
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	};

	private static final TrustManager[] X509_MANAGERS = new TrustManager[] { X509 };

	/**
	 * Creates a new BogusTrustManagerFactory instance
	 */
	@SuppressWarnings("deprecation")
	public BogusTrustManagerFactory() {
		super(new BogusTrustManagerFactorySpi(), new Provider("MinaBogus", 1.0, "") {
			private static final long serialVersionUID = -4024169055312053827L;
		}, "MinaBogus");
	}

	// 一个基于SPI的虚假信任管理工厂服务实现，啥也不做
	private static class BogusTrustManagerFactorySpi extends TrustManagerFactorySpi {
		/**
		 * {@inheritDoc}
		 */
		@Override
		protected TrustManager[] engineGetTrustManagers() {
			return X509_MANAGERS;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected void engineInit(KeyStore keystore) throws KeyStoreException {
			// noop
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected void engineInit(ManagerFactoryParameters managerFactoryParameters)
				throws InvalidAlgorithmParameterException {
			// noop
		}

	}
}
