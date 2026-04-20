import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:dart_sm/dart_sm.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';

import 'test_support.dart';

void main() {
  group('TransferEncryptDioAdapter', () {
    test('encrypts json request and decrypts encrypted response', () async {
      final keyPair = SM2.generateKeyPair();
      final dio = Dio();
      dio.httpClientAdapter = FakeDioAdapter((requestOptions) async {
        final body = asJsonMap(requestOptions.data);
        final payload = decodeTransferPayload(body['transferPayload'] as String);
        expect(body['originalContentType'], 'application/json');
        final sm4Key = SM2.decrypt(
          payload['encryptedKey'] as String,
          keyPair.privateKey,
          cipherMode: C1C3C2,
        );
        final plaintext = SM4.decrypt(
          payload['encryptedData'] as String,
          key: asciiToHex(sm4Key),
        );

        expect(jsonDecode(plaintext), <String, dynamic>{'name': 'dio'});

        final responseJson = jsonEncode(<String, dynamic>{
          'transferPayload': encodeTransferPayload(<String, dynamic>{
            'encryptedData': SM4.encrypt(
              jsonEncode(<String, dynamic>{'ok': true}),
              key: asciiToHex(sm4Key),
            ),
            'contentMd5': md5
                .convert(utf8.encode(jsonEncode(<String, dynamic>{'ok': true})))
                .toString(),
            'timestamp': DateTime.now().millisecondsSinceEpoch,
          }),
          'originalContentType': 'application/json',
        });

        return ResponseBody.fromString(
          responseJson,
          200,
          headers: <String, List<String>>{
            'content-type': <String>['application/json;charset=UTF-8'],
            'x-transfer-encrypted': <String>['true'],
          },
        );
      });

      final adapter = TransferEncryptDioAdapter(
        dio: dio,
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
      );

      final result = await adapter.postJson(
        '/api/json',
        body: <String, dynamic>{'name': 'dio'},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('encrypts form request and keeps options merge behavior', () async {
      final keyPair = SM2.generateKeyPair();
      final dio = Dio();
      dio.httpClientAdapter = FakeDioAdapter((requestOptions) async {
        expect(requestOptions.method, 'POST');
        expect(
          requestOptions.headers[Headers.contentTypeHeader],
          Headers.formUrlEncodedContentType,
        );
        expect(requestOptions.headers['x-trace-id'], 'trace-001');

        final body = Uri.splitQueryString(requestOptions.data as String);
        final payload = decodeTransferPayload(body['transferPayload'] as String);
        final sm4Key = SM2.decrypt(
          payload['encryptedKey'] as String,
          keyPair.privateKey,
          cipherMode: C1C3C2,
        );
        final plaintext = SM4.decrypt(
          payload['encryptedData'] as String,
          key: asciiToHex(sm4Key),
        );
        expect(plaintext, 'name=dio&age=18');

        return ResponseBody.fromString(
          jsonEncode(<String, dynamic>{'ok': true}),
          200,
          headers: <String, List<String>>{
            'content-type': <String>['application/json;charset=UTF-8'],
          },
        );
      });

      final adapter = TransferEncryptDioAdapter(
        dio: dio,
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
      );

      final result = await adapter.postForm(
        '/api/form',
        body: <String, dynamic>{'name': 'dio', 'age': 18},
        headers: <String, dynamic>{'x-trace-id': 'trace-001'},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('adds multipart md5 fields when uploading with dio', () async {
      final dio = Dio();
      dio.httpClientAdapter = FakeDioAdapter((requestOptions) async {
        final formData = requestOptions.data as FormData;
        final fields = Map<String, String>.fromEntries(formData.fields);

        expect(fields['bizType'], 'dio-demo');
        expect(
          fields['__md5_files__0'],
          md5.convert(const <int>[1, 2, 3]).toString(),
        );
        expect(
          fields['__md5_files__1'],
          md5.convert(const <int>[4, 5, 6]).toString(),
        );

        return ResponseBody.fromString(
          jsonEncode(<String, dynamic>{'uploaded': true}),
          200,
          headers: <String, List<String>>{
            'content-type': <String>['application/json;charset=UTF-8'],
          },
        );
      });

      final adapter = TransferEncryptDioAdapter(
        dio: dio,
        publicKey: 'mock-public-key',
      );

      final result = await adapter.upload(
        url: 'http://localhost:8080/api/upload',
        fields: <String, dynamic>{'bizType': 'dio-demo'},
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

    test('supports single-file upload convenience api with dio', () async {
      final dio = Dio();
      dio.httpClientAdapter = FakeDioAdapter((requestOptions) async {
        final formData = requestOptions.data as FormData;
        final fields = Map<String, String>.fromEntries(formData.fields);

        expect(formData.files, hasLength(1));
        expect(
          fields['__md5_file'],
          md5.convert(const <int>[9, 8, 7]).toString(),
        );

        return ResponseBody.fromString(
          jsonEncode(<String, dynamic>{'uploaded': true}),
          200,
          headers: <String, List<String>>{
            'content-type': <String>['application/json;charset=UTF-8'],
          },
        );
      });

      final adapter = TransferEncryptDioAdapter(
        dio: dio,
        publicKey: 'mock-public-key',
      );

      final result = await adapter.uploadSingle(
        url: 'http://localhost:8080/api/upload',
        file: TransferEncryptFile(
          fieldName: 'file',
          filename: 'single.bin',
          bytes: const <int>[9, 8, 7],
        ),
      );

      expect(result, <String, dynamic>{'uploaded': true});
    });

    test('verifies binary download md5 header for dio requests', () async {
      final bytes = utf8.encode('dio file');
      final dio = Dio();
      dio.httpClientAdapter = FakeDioAdapter((requestOptions) async {
        return ResponseBody.fromBytes(
          bytes,
          200,
          headers: <String, List<String>>{
            'content-type': <String>['application/octet-stream'],
            'x-transfer-content-md5': <String>[md5.convert(bytes).toString()],
          },
        );
      });

      final adapter = TransferEncryptDioAdapter(
        dio: dio,
        publicKey: 'mock-public-key',
      );

      final response = await adapter.requestBinary(
        url: 'http://localhost:8080/api/file',
      );

      expect(utf8.decode(response.bytes), 'dio file');
      expect(response.contentMd5, md5.convert(bytes).toString());
    });

    test('decodes binary json error body for dio requests', () async {
      final keyPair = SM2.generateKeyPair();
      final dio = Dio();
      dio.httpClientAdapter = FakeDioAdapter((requestOptions) async {
        final body = asJsonMap(requestOptions.data);
        final payload = decodeTransferPayload(body['transferPayload'] as String);
        final sm4Key = SM2.decrypt(
          payload['encryptedKey'] as String,
          keyPair.privateKey,
          cipherMode: C1C3C2,
        );

        final errorJson = jsonEncode(<String, dynamic>{
          'code': 'EXPORT_FAILED',
          'message': 'no permission',
        });

        return ResponseBody.fromString(
          jsonEncode(<String, dynamic>{
            'transferPayload': encodeTransferPayload(<String, dynamic>{
              'encryptedData': SM4.encrypt(
                errorJson,
                key: asciiToHex(sm4Key),
              ),
              'contentMd5': md5.convert(utf8.encode(errorJson)).toString(),
              'timestamp': DateTime.now().millisecondsSinceEpoch,
            }),
            'originalContentType': 'application/json',
          }),
          403,
          headers: <String, List<String>>{
            'content-type': <String>['application/json;charset=UTF-8'],
            'x-transfer-encrypted': <String>['true'],
          },
        );
      });

      final adapter = TransferEncryptDioAdapter(
        dio: dio,
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
      );

      await expectLater(
        () => adapter.requestBinary(
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
                  'message': 'no permission',
                },
              ),
        ),
      );
    });
  });
}
