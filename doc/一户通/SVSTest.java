package com.zayk.svs.test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

import com.zayk.svs.api.ZaSVSApi;
import com.zayk.svs.api.debug.Log;
import com.zayk.svs.api.exception.ZAYKException;
import com.zayk.svs.util.ConstUtil;
import com.zayk.svs.util.UtilTool;
import com.zayk.svs.util.encoders.Base64;
import com.zayk.svs.util.encoders.Hex;

public class SVSTest {

	public static String bytesToHexString(byte[] b) {
		String hexs = "";
		for (int i = 0; i < b.length; i++) {
			String hex = Integer.toHexString(b[i] & 0xFF);
			if (hex.length() == 1) {
				hex = '0' + hex;
			}
			hexs = hexs + hex.toUpperCase();
		}
		return hexs;
	}

	String Sm2_sign_1 = "sign_2_xian";
	static final String userId_str = "1234567812345678";
	static final byte[] userId_byte = userId_str.getBytes();

	static byte[] certData = null;
	static byte[] pubKey = null;
	static byte[] testData_all = "1234567812345678".getBytes();
	static byte[] testData_half = "1234567812345678".getBytes();

	public static int signMethod = ConstUtil.SGD_SM3_SM2;

	// ConstUtil.SGD_ECC_ED25519;
	public static int hashMethod = ConstUtil.SGD_SM3;
	public static int summtryMethod = ConstUtil.SGD_AES_ECB;

	public static void main(String[] args) throws Exception {

		int cmdType = 0;
		Scanner scanner = new Scanner(System.in);

//		String ip = "120.192.18.46";
//		String port = "13558";
//		String SocketTimeOut = "50";	
//		String ConnectCount = "5";
//		String selectTime = "20";
//		ZaSVSApi.InitServiceConnect(ip, port, SocketTimeOut, ConnectCount,selectTime);

		// ZaSVSApi.InitServiceConnect("C:\\Users\\Administrator\\zayksvs.ini");
		ZaSVSApi.InitServiceConnect(null);

		while (true) {

			boolean exit = false;
			printCmdHead();
			cmdType = GetInputInt(scanner, "Input: ");

			switch (cmdType) {
			case 0:
				testExportCert(scanner);
				break;
			case 1:
				testHash();
				break;
			case 2:
				testHashStep();
				break;
			case 3:
				testRandom(scanner);
				break;
			case 4:
				testParseCert();
				break;
			case 5:
				testValidateCert();
				break;
			case 6:
				testSignData(scanner);
				break;
			case 7:
				testSignDataStep(scanner);
				break;
			case 8:
				testSignMessage(scanner);
				break;
			case 9:
				testSignMessageStep(scanner);
				break;
			case 10:
				testSymmetry();
				break;
			case 11:
				testEncryptData(scanner);
				break;
			case 12:
				testSignDataByCertId(scanner);
				break;
			case 13:
				testSignMessageByCertId(scanner);
				break;
			case 14:
				testSignXML(scanner);
				;
				break;
			case 15:
				testVerifySignedXML(scanner);
				break;

			case 88:
				exit = true;
				break;
			}

			if (exit) {
				break;
			}

			System.out.println("\nPress Enter key to continue ...");
			scanner.nextLine();
			scanner.nextLine();
		}

		scanner.close();
		ZaSVSApi.CloseServiceConnect();
		System.out.println("==============签名验证服务器接口测试完成=================");
	}

	public static void printCmdHead() {
		System.out.println("==============签名验证服务间接口测试==================");
		System.out.println("0.导出证书                         1.摘要运算         2.多包摘要运算");
		System.out.println("3.随机数                              4.解析证书         5.验证证书");
		System.out.println("6.单包数据签名验签     7.多包数据签名验签");
		System.out.println("8.单包消息签名验签     9.多包消息签名验签");
		System.out.println("10.对称密钥加解密       11.数字信封加解密");
		System.out.println("12.单包数据签名验签(通过证书标志)");
		System.out.println("13.单包消息签名验签(通过证书标志)");
		System.out.println("14.XML签名");
		System.out.println("15.XML验签");
	}

	static void testRandom(Scanner scanner) throws ZAYKException {
		int length = GetInputInt(scanner, "Input Length: ");
		length = Math.max(16, length);
		length = Math.min(256, length);

		byte[] outData = ZaSVSApi.Random(length);
		if (outData == null) {
			System.out.println("---------------------->Random Err");
		} else {
			Log.writeTransData(outData, "Random_resp_data.txt");
			System.out.println("====================>Random OK:(length=" + length + ")" + Hex.toHexString(outData));
		}
	}

	static void testExportCert(Scanner scanner) throws ZAYKException {
		String certId = GetInputString(scanner, "Input CertID: ");
		certData = ZaSVSApi.ExportCert(certId.getBytes());
		if (certData == null) {
			System.out.println("---------------------->ExportCert Err");
		} else {
			Log.writeTransData(certData, "ExportCert_resq_data.txt");
			System.out.println(" new String(certData): " + new String(certData));
			System.out.println("bytesToHexStrin(certData): " + bytesToHexString(certData));
			System.out.println("====================>ExportCert OK");
		}

		pubKey = ZaSVSApi.ParseCert(ConstUtil.SGD_CERT_DER_PUBLIC_KEY, certData);
		if (pubKey == null) {
			System.out.println("---------------------->ParseCert SGD_CERT_DER_PUBLIC_KEY Err");
		} else {
			Log.writeTransData(pubKey, "ParseCert_resp_data_SGD_CERT_DER_PUBLIC_KEY.txt");
			System.out
					.println("====================>ParseCert OK, SGD_CERT_DER_PUBLIC_KEY = " + Hex.toHexString(pubKey));
		}

		byte[] subject = ZaSVSApi.ParseCert(ConstUtil.SGD_CERT_SUBJECT, certData);
		if (subject == null) {
			System.out.println("---------------------->ParseCert SGD_CERT_SUBJECT Err");
		} else {
			Log.writeTransData(subject, "ParseCert_resp_data_SGD_CERT_SUBJECT.txt");
			System.out.println("====================>ParseCert OK, SGD_CERT_SUBJECT = " + new String(subject));
		}

		subject = ZaSVSApi.ParseCert(ConstUtil.SGD_CERT_ISSUER, certData);
		if (subject == null) {
			System.out.println("---------------------->ParseCert SGD_CERT_ISSUER Err");
		} else {
			Log.writeTransData(subject, "ParseCert_resp_data_SGD_CERT_ISSUER.txt");
			System.out.println("====================>ParseCert OK, SGD_CERT_ISSUER = " + new String(subject));
		}
	}

	static void testHash() throws ZAYKException {
		byte[] hashData = null;
		if (hashMethod == ConstUtil.SGD_SM3) {
			hashData = ZaSVSApi.Hash(hashMethod, Base64.decode(pubKey), userId_byte, testData_all);
		} else {
			hashData = ZaSVSApi.Hash(hashMethod, null, null, testData_all);
		}
		if (hashData == null) {
			System.out.println("---------------------->Hash Err");
		} else {
			Log.writeTransData(hashData, "hash_resp_data.txt");
			System.out.println("====================>Hash OK = " + Hex.toHexString(hashData));
		}
	}

	static void testHashStep() throws ZAYKException {
		System.out.println("===========================>多步hash摘要运算初始化");
		byte[] hashOut = null;
		if (hashMethod == ConstUtil.SGD_SM3) {
			hashOut = ZaSVSApi.HashInit(hashMethod, Base64.decode(pubKey), userId_byte, testData_half);
		} else {
			hashOut = ZaSVSApi.HashInit(hashMethod, null, null, testData_half);
		}
		if (hashOut == null) {
			System.out.println("---------------------->HashDataInit Err");
		} else {
			Log.writeTransData(hashOut, "HashInit_resp_data.txt");
			System.out.println("====================>HashDataInit OK");
		}
		// 多步hash更新
		hashOut = ZaSVSApi.HashUpdate(hashMethod, hashOut, testData_half);
		if (hashOut == null) {
			System.out.println("---------------------->HashDataUpdate Err");
		} else {
			Log.writeTransData(hashOut, "HashDataUpdate_resp_data.txt");
			System.out.println("====================>HashDataUpdate OK");
		}
		// 多步hash结束
		byte[] hashData = ZaSVSApi.HashFinal(hashMethod, hashOut);
		if (hashData == null) {
			System.out.println("---------------------->HashDataFinal Err");
		} else {
			Log.writeTransData(hashData, "HashDataFinal_resp_data.txt");
			System.out.println("====================>HashDataFinal OK = " + Hex.toHexString(hashData));
		}
	}

	static void testParseCert() throws ZAYKException {
		for (int i = 0; i < 7; i++) {
			byte[] tt = ZaSVSApi.ParseCert((int) eleList.get(i), certData);
			if (tt == null) {
				System.out.println("-------------------->" + eleListName.get(eleList.get(i)) + " = ParseCert Err.");
			} else {
				Log.writeTransData(tt, "ParseCert_resp_data_" + Integer.toHexString((int) eleList.get(i)) + ".txt");
				System.out.println("====================>" + eleListName.get(eleList.get(i)) + " = ParseCert OK.");
			}
		}
	}

	static void testValidateCert() throws ZAYKException {
		int rv = ZaSVSApi.ValidateCert(certData, false);
		if (rv == -1) {
			System.out.println("---------------------->ValidateCert Err");
		} else {
			System.out.println("====================>ValidateCert = " + rv);
		}
	}

	static void testSignData(Scanner scanner) throws ZAYKException {
		int keyIndex = GetInputInt(scanner, "Input Key Num:");

		byte[] SignData = ZaSVSApi.SignData(signMethod, keyIndex, "".getBytes(), testData_all.length, testData_all);
		if (SignData == null) {
			System.out.println("---------------------->SignData Err");
		} else {
			Log.writeTransData(SignData, "SignData_resp_data.txt");
			System.out.println("====================>SignData OK = " + new String(Base64.encode(SignData)));
			System.out.println("====================>SignData OK = " + bytesToHexString(SignData));
		}

//		byte[] SignData = Base64.decode("MEYCIQChwQkvrIx3YjRtGZn/ufCY74pSYxUqTshM2Fvkp/NAJgIhANuZ0U4WoHbatsHz+SCpLMkjJV5Rnp9Gnok3xKEF2y9W");

		boolean ret = ZaSVSApi.VerifySignedData(1, certData, null, signMethod, testData_all, SignData, 1);
		if (!ret) {
			System.out.println("---------------------->VerifySignedData Err");
		} else {
			System.out.println("====================>VerifySignedData OK");
		}

	}

	static void testSignDataStep(Scanner scanner) throws ZAYKException {
		int keyIndex = GetInputInt(scanner, "Input Key Num:");
		System.out.println("===========================>多步数据签名验证初始化");
		byte[] hashOut = null;
		if (signMethod == ConstUtil.SGD_SM3_SM2) {
			hashOut = ZaSVSApi.SignDataInit(signMethod, Base64.decode(pubKey), userId_byte.length, userId_byte,
					testData_half.length, testData_half);
		} else {
			hashOut = ZaSVSApi.SignDataInit(signMethod, null, 16, null, testData_half.length, testData_half);
		}
		if (hashOut == null) {
			System.out.println("---------------------->SignDataInit Err");
		} else {
			Log.writeTransData(hashOut, "SignDataInit_resp_data.txt");
			System.out.println("====================>SignDataInit OK");
		}
		// 多步数字签名更新
		hashOut = ZaSVSApi.SignDataUpdate(signMethod, hashOut.length, hashOut, testData_half.length, testData_half);
		if (hashOut == null) {
			System.out.println("---------------------->SignDataUpdate Err");
		} else {
			Log.writeTransData(hashOut, "SignDataUpdate_resp_data.txt");
			System.out.println("====================>SignDataUpdate OK");
		}
		// 多包数字签名结束

		byte[] SignData = ZaSVSApi.SignDataFinal(signMethod, keyIndex, "".getBytes(), hashOut.length, hashOut);
		if (SignData == null) {
			System.out.println("---------------------->SignDataFinal Err");
		} else {
			Log.writeTransData(SignData, "SignDataFinal_resp_data.txt");
			System.out.println("====================>SignDataFinal OK = " + Hex.toHexString(SignData));
		}

		// 多包验证数字签名初始化
		if (signMethod == ConstUtil.SGD_SM3_SM2) {
			hashOut = ZaSVSApi.VerifySignedDataInit(signMethod, Base64.decode(pubKey), userId_byte.length, userId_byte,
					testData_half.length, testData_half);
		} else {
			hashOut = ZaSVSApi.VerifySignedDataInit(signMethod, null, 0, null, testData_half.length, testData_half);
		}
		if (hashOut == null) {
			System.out.println("---------------------->VerifySignedDataInit Err");
		} else {
			Log.writeTransData(hashOut, "VerifySignedDataInit_resp_data.txt");
			System.out.println("====================>VerifySignedDataInit OK");
		}
		// 多包验证数字签名更新
		hashOut = ZaSVSApi.VerifySignedDataUpdate(signMethod, hashOut.length, hashOut, testData_half.length,
				testData_half);
		if (hashOut == null) {
			System.out.println("---------------------->VerifySignedDataUpdate Err");
		} else {
			Log.writeTransData(hashOut, "VerifySignedDataUpdate_resp_data.txt");
			System.out.println("====================>VerifySignedDataUpdate OK");
		}
		// 多包验证数字签名结束
		boolean ret = ZaSVSApi.VerifySignedDataFinal(signMethod, 1, certData, "".getBytes(), hashOut.length, hashOut,
				SignData, 1);
		if (ret == false) {
			System.out.println("---------------------->VerifySignedDataFinal Err");
		} else {

			System.out.println("====================>VerifySignedDataFinal OK");
		}
	}

	static void testSignMessage(Scanner scanner) throws ZAYKException {
		// 单步消息签名
		System.out.println("===========================>单步消息签名验证");
		int keyIndex = GetInputInt(scanner, "Input Key Num:");
		byte[] SignData = ZaSVSApi.SignMessage(signMethod, keyIndex, "".getBytes(), testData_all.length, testData_all,
				false, true, true, false, false);
		if (SignData == null) {
			System.out.println("---------------------->SignMessage Err");
		} else {
			Log.writeTransData(SignData, "SignMessage_resp_data.txt");
			System.out.println("====================>SignMessage OK");
			System.out.println("====================>SignMessage OK = " + bytesToHexString(SignData));

		}
		// 单包消息验证
		boolean ret = ZaSVSApi.VerifySignedMessage(testData_all.length, testData_all, SignData, false, false, true,
				false, false);
		if (ret == false) {
			System.out.println("---------------------->VerifySignedMessage Err");
		} else {
			System.out.println("====================>VerifySignedMessage OK");
		}
	}

	static void testSignMessageStep(Scanner scanner) throws ZAYKException {
		// 多步消息签名初始化
		System.out.println("===========================>多步消息签名验证");
		int keyIndex = GetInputInt(scanner, "Input Key Num:");
		byte[] hashOut = null;
		if (signMethod == ConstUtil.SGD_SM3_SM2) {
			hashOut = ZaSVSApi.SignMessageInit(signMethod, Base64.decode(pubKey), userId_byte.length, userId_byte,
					testData_all.length, testData_all);
		} else {
			hashOut = ZaSVSApi.SignMessageInit(signMethod, null, 0, null, testData_all.length, testData_all);
		}
		if (hashOut == null) {
			System.out.println("---------------------->SignMessageInit Err");
		} else {
			Log.writeTransData(hashOut, "SignMessageInit_resp_data.txt");
			System.out.println("====================>SignMessageInit OK");
		}
		// 多步消息更新
		hashOut = ZaSVSApi.SignMessageUpdate(signMethod, hashOut.length, hashOut, testData_all.length, testData_all);
		if (hashOut == null) {
			System.out.println("---------------------->SignMessageUpdate Err");
		} else {
			Log.writeTransData(hashOut, "SignMessageUpdate_resp_data.txt");
			System.out.println("====================>SignMessageUpdate OK");
		}
		// 多步消息签名结束
		byte[] SignData = ZaSVSApi.SignMessageFinal(signMethod, keyIndex, "".getBytes(), hashOut.length, hashOut);
		if (SignData == null) {
			System.out.println("---------------------->SignMessageFinal Err");
		} else {
			Log.writeTransData(SignData, "SignMessageFinal_resp_data.txt");
			System.out.println("====================>SignMessageFinal OK");
		}

		// 多包验证消息签名初始化
		if (signMethod == ConstUtil.SGD_SM3_SM2) {
			hashOut = ZaSVSApi.VerifySignedMessageInit(signMethod, Base64.decode(pubKey), userId_byte.length,
					userId_byte, testData_all.length, testData_all);
		} else {
			hashOut = ZaSVSApi.VerifySignedMessageInit(signMethod, null, 0, null, testData_all.length, testData_all);
		}
		if (hashOut == null) {
			System.out.println("---------------------->VerifySignedMessageInit Err");
		} else {
			Log.writeTransData(hashOut, "VerifySignedMessageInit_resp_data.txt");
			System.out.println("====================>VerifySignedMessageInit OK");
		}
		// 多包验证消息签名更新
		hashOut = ZaSVSApi.VerifySignedMessageUpdate(signMethod, hashOut.length, hashOut, testData_all.length,
				testData_all);
		if (hashOut == null) {
			System.out.println("---------------------->VerifySignedMessageUpdate Err");
		} else {
			Log.writeTransData(hashOut, "VerifySignedMessageUpdate_resp_data.txt");
			System.out.println("====================>VerifySignedMessageUpdate OK");
		}
		// 多包验证消息签名结束
		boolean ret = ZaSVSApi.VerifySignedMessageFinal(signMethod, hashOut.length, hashOut, SignData);
		if (ret == false) {
			System.out.println("---------------------->VerifySignedMessageFinal Err");
		} else {
			System.out.println("====================>VerifySignedMessageFinal OK");
		}
	}

	static void testSymmetry() throws ZAYKException {
		summtryMethod = ConstUtil.SGD_SM4_ECB;
		{
			byte[] resultByte = ZaSVSApi.Symmetry(summtryMethod, 1, false, "1111111111111111".getBytes(),
					"8888888888888888".getBytes(), testData_all);
			if (null != resultByte) {
				byte[] resultByte1 = ZaSVSApi.Symmetry(summtryMethod, 0, false, "1111111111111111".getBytes(),
						"8888888888888888".getBytes(), resultByte);
				if (Arrays.equals(resultByte1, testData_all)) {
					System.out.println("---------------------->Symmetry OK = " + Hex.toHexString(resultByte));
				}
			} else {
				System.out.println("---------------------->Symmetry Err");
			}
		}

		{
			byte[] resultByte = ZaSVSApi.Symmetry(summtryMethod, 1, false, "8888888888888888".getBytes(), testData_all,
					1);
			if (null != resultByte) {
				byte[] resultByte1 = ZaSVSApi.Symmetry(summtryMethod, 0, false, "8888888888888888".getBytes(),
						resultByte, 1);
				if (Arrays.equals(resultByte1, testData_all)) {
					System.out.println("---------------------->Symmetry OK = " + Hex.toHexString(resultByte));
				}
			} else {
				System.out.println("Symmetry Err");
			}
		}
	}

	static void testEncryptData(Scanner scanner) throws ZAYKException {
		String certId = GetInputString(scanner, "Input CertID: ");
		byte[] resultByte = null;
		for (int i = 0; i < 2; i++) {
			resultByte = ZaSVSApi.EncryptData(certId.getBytes(), testData_all);

			if (null != resultByte) {
				System.out.println("======================>EncryptData OK." + i);

				byte[] resultByte1 = ZaSVSApi.DecryptData(certId, resultByte);
				if (Arrays.equals(resultByte1, testData_all)) {
					System.out.println("======================>DecryptData OK." + i);
				} else {
					System.out.println("---------------------->DecryptData Error." + i);
					break;
				}
			} else {
				System.out.println("---------------------->EncryptData Error." + i);
				break;
			}
		}
	}

	static void testSignDataByCertId(Scanner scanner) throws ZAYKException {
		System.out.println("===========================>单包消息签名验签(通过证书标志)");
		ByteArrayOutputStream retValue = new ByteArrayOutputStream();
		byte SignData[] = null;

		String certId = GetInputString(scanner, "Input CertID: ");
		int digestMethod = GetInputInt(scanner,
				"Input digestMethod(1:SM3/2:SHA1/4:SHA256/5:ED25519/6:SHA256_RSAPSS): ");

		if (digestMethod == 5) {
			digestMethod = ConstUtil.SGD_ECC_ED25519;
		} else if (digestMethod == 6) {
			digestMethod = ConstUtil.SGD_SHA256_RSAPSS;
		}
		int result = ZaSVSApi.SignDataByCertId(certId, "".getBytes(), digestMethod, testData_all, retValue);

		if (result != 0) {
			System.out.println("---------------------->SignData Err = " + result);
		} else {
			SignData = retValue.toByteArray();
			Log.writeTransData(SignData, "SignData_resp_data.txt");
			System.out.println("====================>SignData OK, length = " + SignData.length + ", Data = "
					+ new String(Base64.encode(SignData)));
		}

		boolean ret = ZaSVSApi.VerifySignedData(3, null, certId.getBytes(), digestMethod, testData_all, SignData, 1);
		if (!ret) {
			System.out.println("---------------------->VerifySignedData Err = " + result);
		} else {
			System.out.println("====================>VerifySignedData OK");
		}
	}

	static void testSignMessageByCertId(Scanner scanner) throws ZAYKException {
		System.out.println("===========================>单包数据签名验签(通过证书标志)");
		ByteArrayOutputStream retValue = new ByteArrayOutputStream();
		byte SignData[] = null;

		int flag = 0;
		boolean bAttach = false;
		String certId = GetInputString(scanner, "Input CertID: ");
		int tsa = GetInputInt(scanner, "TSA(0=no, 1=yes): ");
		int attach = GetInputInt(scanner, "Attach(0=no, 1=yes): ");
		if (tsa == 1) {
			flag = flag + 1;
		}
		if (attach == 1) {
			bAttach = true;
		}

		int result = ZaSVSApi.SignMessageByCertId(certId, "".getBytes(), testData_all, flag, false, bAttach, true,
				false, false, retValue);
		if (result != 0) {
			System.out.println("---------------------->SignMessageByCertId Err");
		} else {
			SignData = retValue.toByteArray();
			;
			Log.writeTransData(SignData, "SignMessage_resp_data.txt");
			System.out.println("====================>SignMessageByCertId OK");
		}

		boolean ret = ZaSVSApi.VerifySignedMessage(testData_all.length, testData_all, SignData, false, true, true,
				false, false);
		if (ret == false) {
			System.out.println("---------------------->VerifySignedMessage Err");
		} else {
			System.out.println("====================>VerifySignedMessage OK");
		}
	}

	static void testSignXML(Scanner scanner) {

		byte[] resultByte = null;
		try {

			resultByte = ZaSVSApi.SignXML(ConstUtil.SGD_SHA1_RSA, 1, "".getBytes(),
					"<insert id=\"insertSecretKeyUpdate\">\r\n</insert>".getBytes(), 1);
			if (null != resultByte) {
				System.out.println("======================>SignXML OK.");
				System.out.println("resultByte: " + Base64.toBase64String(resultByte));
			} else {
				System.out.println("---------------------->SignXML Error.");
			}

		} catch (ZAYKException e) {
			System.out.println(
					"---------------------->testSignXML Err code:" + e.getErrCode() + "   msg:" + e.getMessage());
		} catch (Exception e) {
			System.out.println("---------------------->testSignXML system Err  msg:" + e.getMessage());
		}
	}

	static void testVerifySignedXML(Scanner scanner) {

		byte[] resultByte = null;
		boolean resultBoolean = false;
		try {
			byte[] inData = "<insert id=\"insertSecretKeyUpdate\">\r\n</insert>".getBytes();
			resultByte = ZaSVSApi.SignXML(ConstUtil.SGD_SHA1_RSA, 1, "".getBytes(), inData, 1);
			if (null != resultByte) {
				System.out.println("======================>SignXML OK.");
				System.out.println("resultByte: " + Base64.toBase64String(resultByte));
			} else {
				System.out.println("---------------------->SignXML Error.");
			}

			resultBoolean = ZaSVSApi.VerifySignedXML(inData, resultByte);
			if (resultBoolean) {
				System.out.println("======================>VerifySignedXML OK.");
			} else {
				System.out.println("---------------------->VerifySignedXML Error.");
			}

		} catch (ZAYKException e) {
			System.out.println("---------------------->testVerifySignedXML Err code:" + e.getErrCode() + "   msg:"
					+ e.getMessage());
		} catch (Exception e) {
			System.out.println("---------------------->testVerifySignedXML system Err  msg:" + e.getMessage());
		}
	}

	public static int logLevel = 2;// 0表示err，1表示info,2表示debugger
	public static boolean writeFile = true;

	static List<Integer> eleList = null;
	static HashMap<Integer, String> eleListName = null;
	static {
		eleList = new ArrayList<Integer>();
		eleList.add(ConstUtil.SGD_CERT_VERSION);// 证书版本
		eleList.add(ConstUtil.SGD_CERT_SERIAL);// 证书序列号
		eleList.add(ConstUtil.SGD_CERT_ISSUER);// 证书颁发者信息
		eleList.add(ConstUtil.SGD_CERT_VALID_TIME);// 证书有效期
		eleList.add(ConstUtil.SGD_CERT_SUBJECT);// 证书拥有者信息
		eleList.add(ConstUtil.SGD_CERT_DER_PUBLIC_KEY);// 证书公钥信息
		eleList.add(ConstUtil.SGD_CERT_DER_EXTENSIONS);// 证书扩展项信息
		eleList.add(ConstUtil.SGD_EXT_AUTHORITYKEYIDENTIFIER_INFO);// 颁发者密钥标示符
		eleList.add(ConstUtil.SGD_EXT_SUBJECTKEYIDENTIFIER_INFO);// 证书持有者密钥标识符
		eleList.add(ConstUtil.SGD_EXT_KEYUSAGE_INFO);// 密钥用途
		eleList.add(ConstUtil.SGD_EXT_PRIVATEKEYUSAGEPERIOD_INFO);// 私钥有效期
		eleList.add(ConstUtil.SGD_EXT_CERTIFICATEPOLICIES_INFO);// 证书策略
		eleList.add(ConstUtil.SGD_EXT_POLICYMAPPINGS_INFO);// 策略映射
		eleList.add(ConstUtil.SGD_EXT_BASICCONSTRAINTS_INFO);// 基本限制
		eleList.add(ConstUtil.SGD_EXT_POLICYCONSTRAINTS_INFO);// 策略限制
		eleList.add(ConstUtil.SGD_EXT_EXTKEYUSAGE_INFO);// 扩展密钥用途
		eleList.add(ConstUtil.SGD_EXT_CRLDISTRIBUTIONPOINTS_INFO);// CRL发布点
		eleList.add(ConstUtil.SGD_EXT_NETSCAPE_CERT_TYPE_INFO);// netScape属性
		eleList.add(ConstUtil.SGD_EXT_SELFDEFINED_EXTENSION_INFO);// 私有的自定义扩展项
		eleList.add(ConstUtil.SGD_CERT_ISSUE_CN);// 证书颁发者CN
		eleList.add(ConstUtil.SGD_CERT_ISSUE_O);// 证书颁发者O
		eleList.add(ConstUtil.SGD_CERT_ISSUE_OU);// 证书颁发者OU
		eleList.add(ConstUtil.SGD_CERT_SUBJECT_CN);// 证书拥有者信息CN
		eleList.add(ConstUtil.SGD_CERT_SUBJECT_O);// 证书拥有着O
		eleList.add(ConstUtil.SGD_CERT_SUBJECT_OU);// 证书拥有着OU
		eleList.add(ConstUtil.SGD_CERT_SUBJECT_EMAIL);// 证书拥有着信息EMAIL

		eleListName = new HashMap<Integer, String>();
		eleListName.put(ConstUtil.SGD_CERT_VERSION, "SGD_CERT_VERSION");
		eleListName.put(ConstUtil.SGD_CERT_SERIAL, "SGD_CERT_SERIAL");
		eleListName.put(ConstUtil.SGD_CERT_ISSUER, "SGD_CERT_ISSUER");
		eleListName.put(ConstUtil.SGD_CERT_VALID_TIME, "SGD_CERT_VALID_TIME");
		eleListName.put(ConstUtil.SGD_CERT_SUBJECT, "SGD_CERT_SUBJECT");
		eleListName.put(ConstUtil.SGD_CERT_DER_PUBLIC_KEY, "SGD_CERT_DER_PUBLIC_KEY");
		eleListName.put(ConstUtil.SGD_CERT_DER_EXTENSIONS, "SGD_CERT_DER_EXTENSIONS");
		eleListName.put(ConstUtil.SGD_EXT_AUTHORITYKEYIDENTIFIER_INFO, "SGD_EXT_AUTHORITYKEYIDENTIFIER_INFO");
		eleListName.put(ConstUtil.SGD_EXT_SUBJECTKEYIDENTIFIER_INFO, "SGD_EXT_SUBJECTKEYIDENTIFIER_INFO");
		eleListName.put(ConstUtil.SGD_EXT_KEYUSAGE_INFO, "SGD_EXT_KEYUSAGE_INFO");
		eleListName.put(ConstUtil.SGD_EXT_PRIVATEKEYUSAGEPERIOD_INFO, "SGD_EXT_PRIVATEKEYUSAGEPERIOD_INFO");
		eleListName.put(ConstUtil.SGD_EXT_CERTIFICATEPOLICIES_INFO, "SGD_EXT_CERTIFICATEPOLICIES_INFO");
		eleListName.put(ConstUtil.SGD_EXT_POLICYMAPPINGS_INFO, "SGD_EXT_POLICYMAPPINGS_INFO");
		eleListName.put(ConstUtil.SGD_EXT_BASICCONSTRAINTS_INFO, "SGD_EXT_BASICCONSTRAINTS_INFO");
		eleListName.put(ConstUtil.SGD_EXT_POLICYCONSTRAINTS_INFO, "SGD_EXT_POLICYCONSTRAINTS_INFO");
		eleListName.put(ConstUtil.SGD_EXT_EXTKEYUSAGE_INFO, "SGD_EXT_EXTKEYUSAGE_INFO");
		eleListName.put(ConstUtil.SGD_EXT_CRLDISTRIBUTIONPOINTS_INFO, "SGD_EXT_CRLDISTRIBUTIONPOINTS_INFO");
		eleListName.put(ConstUtil.SGD_EXT_NETSCAPE_CERT_TYPE_INFO, "SGD_EXT_NETSCAPE_CERT_TYPE_INFO");
		eleListName.put(ConstUtil.SGD_EXT_SELFDEFINED_EXTENSION_INFO, "SGD_EXT_SELFDEFINED_EXTENSION_INFO");
		eleListName.put(ConstUtil.SGD_CERT_ISSUE_CN, "SGD_CERT_ISSUE_CN");
		eleListName.put(ConstUtil.SGD_CERT_ISSUE_O, "SGD_CERT_ISSUE_O");
		eleListName.put(ConstUtil.SGD_CERT_ISSUE_OU, "SGD_CERT_ISSUE_OU");
		eleListName.put(ConstUtil.SGD_CERT_SUBJECT_CN, "SGD_CERT_SUBJECT_CN");
		eleListName.put(ConstUtil.SGD_CERT_SUBJECT_O, "SGD_CERT_SUBJECT_O");
		eleListName.put(ConstUtil.SGD_CERT_SUBJECT_OU, "SGD_CERT_SUBJECT_OU");
		eleListName.put(ConstUtil.SGD_CERT_SUBJECT_EMAIL, "SGD_CERT_SUBJECT_EMAIL");
	}

	static int GetInputInt(Scanner scanner, String title) {
		int retValue = 0;
		while (true) {
			try {
				System.out.print(title);
				retValue = scanner.nextInt(10);
			} catch (InputMismatchException e) {
				System.out.println("Input error(should between 1 and " + Integer.MAX_VALUE + ") :" + scanner.next());
				continue;
			}
			break;
		}

		return retValue;
	}

	static String GetInputString(Scanner scanner, String title) {
		String retValue = null;
		while (true) {
			try {
				System.out.print(title);
				retValue = scanner.next();
			} catch (Exception e) {
				System.out.println("Input error: " + scanner.next() + ", Try again.");
				continue;
			}
			if (retValue != null && retValue.length() > 0) {
				break;
			}
		}

		return retValue;
	}

}
