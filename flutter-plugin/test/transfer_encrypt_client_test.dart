import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dart_sm/dart_sm.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';
import 'package:http/http.dart' as http;

import 'test_support.dart';

void main() {
  group('TransferEncryptClient', () {
    test('encrypts json requests and decrypts encrypted json responses', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final wrapper =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final envelope = decodeTransferPayload(
            wrapper['transferPayload'] as String,
          );

          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            envelope['encryptedData'] as String,
            key: asciiToHex(sm4Key),
          );

          expect(
            jsonDecode(plaintext),
            <String, dynamic>{'name': 'alice'},
          );
          expect(wrapper['originalContentType'], 'application/json');

          final responsePayload = encodeTransferPayload(<String, dynamic>{
            'encryptedData': SM4.encrypt(
              jsonEncode(<String, dynamic>{'ok': true}),
              key: asciiToHex(sm4Key),
            ),
            'contentMd5': md5
                .convert(utf8.encode(jsonEncode(<String, dynamic>{'ok': true})))
                .toString(),
            'timestamp': DateTime.now().millisecondsSinceEpoch,
          });

          return jsonResponse(
            <String, dynamic>{
              'transferPayload': responsePayload,
              'originalContentType': 'application/json',
            },
            headers: <String, String>{
              'content-type': 'application/json;charset=UTF-8',
              'x-transfer-encrypted': 'true',
            },
          );
        }),
      );

      final result = await client.postJson(
        '/api/json',
        body: <String, dynamic>{'name': 'alice'},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('encrypts query parameters for get requests', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final uri = request.url;
          expect(uri.path, '/api/query');
          final transferPayload = uri.queryParameters['transferPayload'];
          expect(transferPayload, isNotNull);
          expect(
            uri.queryParameters['originalContentType'],
            'application/x-www-form-urlencoded',
          );
          final envelope = decodeTransferPayload(transferPayload!);

          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            envelope['encryptedData'] as String,
            key: asciiToHex(sm4Key),
          );

          expect(plaintext, 'name=bob');

          return jsonResponse(<String, dynamic>{'ok': true});
        }),
      );

      final result = await client.get(
        '/api/query',
        params: <String, dynamic>{'name': 'bob'},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('preserves existing query parameters when appending encrypted params', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final uri = request.url;
          expect(uri.queryParameters['lang'], 'zh-CN');
          expect(uri.queryParameters['mode'], 'debug');
          expect(uri.queryParameters['transferPayload'], isNotNull);
          expect(
            uri.queryParameters['originalContentType'],
            'application/x-www-form-urlencoded',
          );
          return jsonResponse(<String, dynamic>{'ok': true});
        }),
      );

      final result = await client.get(
        '/api/query?lang=zh-CN&mode=debug',
        params: <String, dynamic>{'name': 'bob'},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('encrypts form requests', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final rawRequest = request as http.Request;
          expect(
            rawRequest.headers['Content-Type'] ??
                rawRequest.headers['content-type'],
            'application/x-www-form-urlencoded;charset=UTF-8',
          );

          final formBody = Uri.splitQueryString(rawRequest.body);
          final envelope = decodeTransferPayload(
            formBody['transferPayload']!,
          );
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            envelope['encryptedData'] as String,
            key: asciiToHex(sm4Key),
          );

          expect(plaintext, 'name=alice&age=18');
          return jsonResponse(<String, dynamic>{'ok': true});
        }),
      );

      final result = await client.postForm(
        '/api/form',
        body: <String, dynamic>{'name': 'alice', 'age': 18},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('adds multipart md5 fields for multi-file uploads', () async {
      final client = TransferEncryptClient(
        publicKey: 'mock-public-key',
        httpClient: CaptureClient((request) async {
          final multipart = request as http.MultipartRequest;

          expect(multipart.files, hasLength(2));
          expect(multipart.fields['bizType'], 'demo');
          expect(
            multipart.fields['__md5_files__0'],
            md5.convert(const <int>[1, 2, 3]).toString(),
          );
          expect(
            multipart.fields['__md5_files__1'],
            md5.convert(const <int>[4, 5, 6]).toString(),
          );

          return jsonResponse(<String, dynamic>{'uploaded': true});
        }),
      );

      final result = await client.upload(
        'http://localhost:8080/api/upload',
        fields: <String, dynamic>{'bizType': 'demo'},
        files: <TransferEncryptFile>[
          TransferEncryptFile(
            fieldName: 'files',
            filename: 'a.bin',
            bytes: const <int>[1, 2, 3],
          ),
          TransferEncryptFile(
            fieldName: 'files',
            filename: 'b.bin',
            bytes: const <int>[4, 5, 6],
          ),
        ],
      );

      expect(result, <String, dynamic>{'uploaded': true});
    });

    test('supports single-file upload convenience api', () async {
      final client = TransferEncryptClient(
        publicKey: 'mock-public-key',
        httpClient: CaptureClient((request) async {
          final multipart = request as http.MultipartRequest;
          expect(multipart.files, hasLength(1));
          expect(
            multipart.fields['__md5_file'],
            md5.convert(const <int>[9, 8, 7]).toString(),
          );
          return jsonResponse(<String, dynamic>{'uploaded': true});
        }),
      );

      final result = await client.uploadSingle(
        'http://localhost:8080/api/upload',
        file: TransferEncryptFile(
          fieldName: 'file',
          filename: 'single.bin',
          bytes: const <int>[9, 8, 7],
        ),
      );

      expect(result, <String, dynamic>{'uploaded': true});
    });

    test('verifies binary download md5 header', () async {
      final bytes = Uint8List.fromList(utf8.encode('hello file'));
      final client = TransferEncryptClient(
        publicKey: 'mock-public-key',
        httpClient: CaptureClient((request) async {
          return binaryResponse(
            bytes,
            headers: <String, String>{
              'content-type': 'application/octet-stream',
              'x-transfer-content-md5': md5.convert(bytes).toString(),
            },
          );
        }),
      );

      final response = await client.download('http://localhost:8080/api/file');

      expect(utf8.decode(response.bytes), 'hello file');
      expect(response.contentMd5, md5.convert(bytes).toString());
    });

    test('throws when binary download md5 header does not match body', () async {
      final bytes = Uint8List.fromList(utf8.encode('hello file'));
      final client = TransferEncryptClient(
        publicKey: 'mock-public-key',
        httpClient: CaptureClient((request) async {
          return binaryResponse(
            bytes,
            headers: <String, String>{
              'content-type': 'application/octet-stream',
              'x-transfer-content-md5': md5
                  .convert(utf8.encode('unexpected'))
                  .toString(),
            },
          );
        }),
      );

      await expectLater(
        () => client.download('http://localhost:8080/api/file'),
        throwsA(
          isA<TransferEncryptException>().having(
            (e) => e.message,
            'message',
            '文件 MD5 校验失败',
          ),
        ),
      );
    });

    test('supports encrypted post request for binary endpoint', () async {
      final keyPair = SM2.generateKeyPair();
      final bytes = Uint8List.fromList(const <int>[10, 11, 12]);
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final wrapper =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final envelope = decodeTransferPayload(
            wrapper['transferPayload'] as String,
          );
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            envelope['encryptedData'] as String,
            key: asciiToHex(sm4Key),
          );
          expect(
            jsonDecode(plaintext),
            <String, dynamic>{'bizId': 1001},
          );
          expect(wrapper['originalContentType'], 'application/json');

          return binaryResponse(
            bytes,
            headers: <String, String>{
              'content-type': 'application/octet-stream',
              'x-transfer-content-md5': md5.convert(bytes).toString(),
            },
          );
        }),
      );

      final response = await client.requestBinary(
        url: '/api/export',
        method: 'POST',
        json: <String, dynamic>{'bizId': 1001},
      );

      expect(response.bytes, orderedEquals(bytes));
    });

    test('throws http exception with decrypted response body', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final wrapper =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final envelope = decodeTransferPayload(
            wrapper['transferPayload'] as String,
          );
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );

          final errorJson = jsonEncode(<String, dynamic>{
            'code': 'BIZ_ERROR',
            'message': 'invalid request',
          });

          return jsonResponse(
            <String, dynamic>{
              'transferPayload': encodeTransferPayload(<String, dynamic>{
                'encryptedData': SM4.encrypt(
                  errorJson,
                  key: asciiToHex(sm4Key),
                ),
                'contentMd5': md5.convert(utf8.encode(errorJson)).toString(),
                'timestamp': DateTime.now().millisecondsSinceEpoch,
              }),
              'originalContentType': 'application/json',
            },
            statusCode: 400,
            headers: <String, String>{
              'content-type': 'application/json;charset=UTF-8',
              'x-transfer-encrypted': 'true',
            },
          );
        }),
      );

      await expectLater(
        () => client.postJson(
          '/api/json',
          body: <String, dynamic>{'name': 'alice'},
        ),
        throwsA(
          isA<TransferEncryptHttpException>()
              .having((e) => e.statusCode, 'statusCode', 400)
              .having(
                (e) => e.responseBody,
                'responseBody',
                <String, dynamic>{
                  'code': 'BIZ_ERROR',
                  'message': 'invalid request',
                },
              ),
        ),
      );
    });

    test('decodes json error body for binary requests when possible', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final wrapper =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final envelope = decodeTransferPayload(
            wrapper['transferPayload'] as String,
          );
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );

          final errorJson = jsonEncode(<String, dynamic>{
            'code': 'EXPORT_FAILED',
            'message': 'permission denied',
          });

          return jsonResponse(
            <String, dynamic>{
              'transferPayload': encodeTransferPayload(<String, dynamic>{
                'encryptedData': SM4.encrypt(
                  errorJson,
                  key: asciiToHex(sm4Key),
                ),
                'contentMd5': md5.convert(utf8.encode(errorJson)).toString(),
                'timestamp': DateTime.now().millisecondsSinceEpoch,
              }),
              'originalContentType': 'application/json',
            },
            statusCode: 403,
            headers: <String, String>{
              'content-type': 'application/json;charset=UTF-8',
              'x-transfer-encrypted': 'true',
            },
          );
        }),
      );

      await expectLater(
        () => client.requestBinary(
          url: '/api/export',
          method: 'POST',
          json: <String, dynamic>{'bizId': 1001},
        ),
        throwsA(
          isA<TransferEncryptHttpException>()
              .having((e) => e.statusCode, 'statusCode', 403)
              .having(
                (e) => e.responseBody,
                'responseBody',
                <String, dynamic>{
                  'code': 'EXPORT_FAILED',
                  'message': 'permission denied',
                },
              ),
        ),
      );
    });
  });
}
