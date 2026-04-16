import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dart_sm/dart_sm.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';
import 'package:http/http.dart' as http;

void main() {
  group('TransferEncryptClient', () {
    test('encrypts json requests and decrypts encrypted json responses', () async {
      final keyPair = SM2.generateKeyPair();
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: _CaptureClient((request) async {
          expect(request, isA<http.Request>());
          final rawRequest = request as http.Request;
          expect(
            rawRequest.headers['Content-Type'],
            contains('application/json'),
          );

          final envelope =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            envelope['encryptedData'] as String,
            key: _asciiToHex(sm4Key),
          );

          expect(
            jsonDecode(plaintext),
            <String, dynamic>{'name': 'alice'},
          );

          return _jsonResponse(
            <String, dynamic>{
              'algorithm': 'SM2_SM4',
              'encryptedData': SM4.encrypt(
                jsonEncode(<String, dynamic>{'ok': true}),
                key: _asciiToHex(sm4Key),
              ),
              'contentMd5': md5
                  .convert(utf8.encode(jsonEncode(<String, dynamic>{'ok': true})))
                  .toString(),
              'originalContentType': 'application/json',
              'timestamp': DateTime.now().millisecondsSinceEpoch,
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
        httpClient: _CaptureClient((request) async {
          final uri = request.url;
          expect(uri.path, '/api/query');
          expect(uri.queryParameters['encryptedKey'], isNotNull);
          expect(uri.queryParameters['encryptedData'], isNotNull);

          final sm4Key = SM2.decrypt(
            uri.queryParameters['encryptedKey']!,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            uri.queryParameters['encryptedData']!,
            key: _asciiToHex(sm4Key),
          );

          expect(plaintext, 'name=bob');

          return _jsonResponse(<String, dynamic>{'ok': true});
        }),
      );

      final result = await client.get(
        '/api/query',
        params: <String, dynamic>{'name': 'bob'},
      );

      expect(result, <String, dynamic>{'ok': true});
    });

    test('adds multipart md5 fields for multi-file uploads', () async {
      final client = TransferEncryptClient(
        publicKey: 'mock-public-key',
        httpClient: _CaptureClient((request) async {
          expect(request, isA<http.MultipartRequest>());
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

          return _jsonResponse(<String, dynamic>{'uploaded': true});
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
        httpClient: _CaptureClient((request) async {
          final multipart = request as http.MultipartRequest;
          expect(multipart.files, hasLength(1));
          expect(
            multipart.fields['__md5_file'],
            md5.convert(const <int>[9, 8, 7]).toString(),
          );
          return _jsonResponse(<String, dynamic>{'uploaded': true});
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
        httpClient: _CaptureClient((request) async {
          return _binaryResponse(
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

    test('supports encrypted post request for binary endpoint', () async {
      final keyPair = SM2.generateKeyPair();
      final bytes = Uint8List.fromList(const <int>[10, 11, 12]);
      final client = TransferEncryptClient(
        publicKey: keyPair.publicKey,
        baseUrl: 'http://localhost:8080',
        httpClient: _CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final envelope =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );
          final plaintext = SM4.decrypt(
            envelope['encryptedData'] as String,
            key: _asciiToHex(sm4Key),
          );
          expect(
            jsonDecode(plaintext),
            <String, dynamic>{'bizId': 1001},
          );

          return _binaryResponse(
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
        httpClient: _CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final envelope =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );

          final errorJson = jsonEncode(<String, dynamic>{
            'code': 'BIZ_ERROR',
            'message': 'invalid request',
          });

          return _jsonResponse(
            <String, dynamic>{
              'algorithm': 'SM2_SM4',
              'encryptedData': SM4.encrypt(
                errorJson,
                key: _asciiToHex(sm4Key),
              ),
              'contentMd5': md5.convert(utf8.encode(errorJson)).toString(),
              'originalContentType': 'application/json',
              'timestamp': DateTime.now().millisecondsSinceEpoch,
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
        httpClient: _CaptureClient((request) async {
          final rawRequest = request as http.Request;
          final envelope =
              jsonDecode(rawRequest.body) as Map<String, dynamic>;
          final sm4Key = SM2.decrypt(
            envelope['encryptedKey'] as String,
            keyPair.privateKey,
            cipherMode: C1C3C2,
          );

          final errorJson = jsonEncode(<String, dynamic>{
            'code': 'EXPORT_FAILED',
            'message': 'permission denied',
          });

          return _jsonResponse(
            <String, dynamic>{
              'algorithm': 'SM2_SM4',
              'encryptedData': SM4.encrypt(
                errorJson,
                key: _asciiToHex(sm4Key),
              ),
              'contentMd5': md5.convert(utf8.encode(errorJson)).toString(),
              'originalContentType': 'application/json',
              'timestamp': DateTime.now().millisecondsSinceEpoch,
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

class _CaptureClient extends http.BaseClient {
  _CaptureClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request) _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}

http.StreamedResponse _jsonResponse(
  Map<String, dynamic> body, {
  int statusCode = 200,
  Map<String, String>? headers,
}) {
  final bytes = utf8.encode(jsonEncode(body));
  return http.StreamedResponse(
    Stream<List<int>>.value(bytes),
    statusCode,
    headers: <String, String>{
      'content-type': 'application/json;charset=UTF-8',
      ...?headers,
    },
  );
}

http.StreamedResponse _binaryResponse(
  List<int> body, {
  int statusCode = 200,
  Map<String, String>? headers,
}) {
  return http.StreamedResponse(
    Stream<List<int>>.value(body),
    statusCode,
    headers: headers ?? const <String, String>{},
  );
}

String _asciiToHex(String text) {
  return text.codeUnits
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
}
