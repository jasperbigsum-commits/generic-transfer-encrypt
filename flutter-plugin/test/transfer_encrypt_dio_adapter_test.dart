import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:dart_sm/dart_sm.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';

void main() {
  group('TransferEncryptDioAdapter', () {
    test('encrypts json request and decrypts encrypted response', () async {
      final keyPair = SM2.generateKeyPair();
      final dio = Dio();
      dio.httpClientAdapter = _FakeDioAdapter((requestOptions) async {
        final body = _asJsonMap(requestOptions.data);
        final payload = _decodeTransferPayload(body['transferPayload'] as String);
        expect(body['originalContentType'], 'application/json');
        final sm4Key = SM2.decrypt(
          payload['encryptedKey'] as String,
          keyPair.privateKey,
          cipherMode: C1C3C2,
        );
        final plaintext = SM4.decrypt(
          payload['encryptedData'] as String,
          key: _asciiToHex(sm4Key),
        );

        expect(jsonDecode(plaintext), <String, dynamic>{'name': 'dio'});

        final responseJson = jsonEncode(<String, dynamic>{
          'transferPayload': _encodeTransferPayload(<String, dynamic>{
            'encryptedData': SM4.encrypt(
              jsonEncode(<String, dynamic>{'ok': true}),
              key: _asciiToHex(sm4Key),
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

    test('adds multipart md5 fields when uploading with dio', () async {
      final dio = Dio();
      dio.httpClientAdapter = _FakeDioAdapter((requestOptions) async {
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

    test('decodes binary json error body for dio requests', () async {
      final keyPair = SM2.generateKeyPair();
      final dio = Dio();
      dio.httpClientAdapter = _FakeDioAdapter((requestOptions) async {
        final body = _asJsonMap(requestOptions.data);
        final payload = _decodeTransferPayload(body['transferPayload'] as String);
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
            'transferPayload': _encodeTransferPayload(<String, dynamic>{
              'encryptedData': SM4.encrypt(
                errorJson,
                key: _asciiToHex(sm4Key),
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

class _FakeDioAdapter implements HttpClientAdapter {
  _FakeDioAdapter(this._handler);

  final Future<ResponseBody> Function(RequestOptions requestOptions) _handler;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    return _handler(options);
  }
}

String _asciiToHex(String text) {
  return text.codeUnits
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
}

Map<String, dynamic> _asJsonMap(Object? data) {
  if (data is Map<String, dynamic>) {
    return data;
  }
  if (data is String) {
    return Map<String, dynamic>.from(jsonDecode(data) as Map);
  }
  return Map<String, dynamic>.from(data as Map);
}

String _encodeTransferPayload(Map<String, dynamic> envelope) {
  return base64Url.encode(utf8.encode(jsonEncode(envelope))).replaceAll('=', '');
}

Map<String, dynamic> _decodeTransferPayload(String payload) {
  var normalized = payload;
  while (normalized.length % 4 != 0) {
    normalized += '=';
  }
  return Map<String, dynamic>.from(
    jsonDecode(utf8.decode(base64Url.decode(normalized))) as Map,
  );
}
