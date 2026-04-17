import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dart_sm/dart_sm.dart';

import 'transfer_encrypt_exception.dart';

class TransferEncryptProtocol {
  TransferEncryptProtocol({
    required this.publicKey,
    Random? random,
  }) : _random = random ?? Random.secure();

  final String publicKey;
  final Random _random;

  String randomSm4Key() {
    const chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    final buffer = StringBuffer();
    for (var index = 0; index < 16; index += 1) {
      buffer.write(chars[_random.nextInt(chars.length)]);
    }
    return buffer.toString();
  }

  TransferEncryptEnvelope createEnvelope({
    required String plaintext,
    required String sm4Key,
  }) {
    final trimmedPublicKey = publicKey.trim();
    if (trimmedPublicKey.isEmpty) {
      throw const TransferEncryptException('publicKey 未配置');
    }

    final encryptedKey = SM2.encrypt(
      sm4Key,
      trimmedPublicKey,
      cipherMode: C1C3C2,
    );
    final encryptedData = SM4.encrypt(
      plaintext,
      key: asciiToHex(sm4Key),
    );
    return TransferEncryptEnvelope(
      encryptedKey: encryptedKey,
      encryptedData: encryptedData,
      contentMd5: md5.convert(utf8.encode(plaintext)).toString(),
      timestamp: DateTime.now().millisecondsSinceEpoch,
    );
  }

  TransferEncryptTransportMessage createTransportMessage({
    required TransferEncryptEnvelope envelope,
    required String originalContentType,
  }) {
    return TransferEncryptTransportMessage(
      transferPayload: encodeTransferPayload(envelope),
      originalContentType: originalContentType,
    );
  }

  TransferEncryptTransportMessage? decodeTransportMessage(dynamic parsed) {
    if (parsed is! Map<String, dynamic>) {
      return null;
    }
    final transferPayload = parsed['transferPayload']?.toString();
    final originalContentType = parsed['originalContentType']?.toString();
    if (transferPayload == null || transferPayload.trim().isEmpty) {
      return null;
    }
    return TransferEncryptTransportMessage(
      transferPayload: transferPayload,
      originalContentType: originalContentType,
    );
  }

  String decryptEnvelope(TransferEncryptEnvelope envelope, String sm4Key) {
    final plaintext = SM4.decrypt(
      envelope.encryptedData,
      key: asciiToHex(sm4Key),
    );
    final actualMd5 = md5.convert(utf8.encode(plaintext)).toString();
    if (actualMd5.toLowerCase() != envelope.contentMd5.toLowerCase()) {
      throw const TransferEncryptException('响应 MD5 校验失败');
    }
    return plaintext;
  }

  String normalizeParameters(Map<String, dynamic> input) {
    final pairs = <String>[];
    for (final entry in input.entries) {
      final key = entry.key;
      final value = entry.value;
      if (value == null) {
        continue;
      }
      if (value is Iterable && value is! String) {
        for (final item in value) {
          pairs.add(
            '${Uri.encodeQueryComponent(key)}='
            '${Uri.encodeQueryComponent(item?.toString() ?? '')}',
          );
        }
        continue;
      }
      pairs.add(
        '${Uri.encodeQueryComponent(key)}='
        '${Uri.encodeQueryComponent(value.toString())}',
      );
    }
    return pairs.join('&');
  }

  Uri appendQueryString(Uri uri, String queryString) {
    if (queryString.isEmpty) {
      return uri;
    }
    if (uri.query.isEmpty) {
      return uri.replace(query: queryString);
    }
    return uri.replace(query: '${uri.query}&$queryString');
  }

  dynamic tryParseJson(String text) {
    try {
      return jsonDecode(text);
    } catch (_) {
      return null;
    }
  }

  String encodeTransferPayload(TransferEncryptEnvelope envelope) {
    final bytes = utf8.encode(jsonEncode(envelope.toJson()));
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  TransferEncryptEnvelope? decodeTransferPayload(String? payload) {
    if (payload == null || payload.trim().isEmpty) {
      return null;
    }
    final normalized = _normalizeBase64Padding(payload.trim());
    final bytes = base64Url.decode(normalized);
    final parsed = jsonDecode(utf8.decode(bytes));
    if (parsed is! Map<String, dynamic>) {
      return null;
    }
    return TransferEncryptEnvelope.fromJson(parsed);
  }

  String _normalizeBase64Padding(String value) {
    final mod = value.length % 4;
    if (mod == 0) {
      return value;
    }
    return value + ('=' * (4 - mod));
  }

  String asciiToHex(String text) {
    final values = text.codeUnits
        .map((value) => value.toRadixString(16).padLeft(2, '0'))
        .join();
    return values.toLowerCase();
  }
}

class TransferEncryptEnvelope {
  const TransferEncryptEnvelope({
    required this.encryptedKey,
    required this.encryptedData,
    required this.contentMd5,
    required this.timestamp,
  });

  factory TransferEncryptEnvelope.fromJson(Map<String, dynamic> json) {
    return TransferEncryptEnvelope(
      encryptedKey: (json['encryptedKey'] ?? '').toString(),
      encryptedData: (json['encryptedData'] ?? '').toString(),
      contentMd5: (json['contentMd5'] ?? '').toString(),
      timestamp: (json['timestamp'] as num?)?.toInt(),
    );
  }

  final String encryptedKey;
  final String encryptedData;
  final String contentMd5;
  final int? timestamp;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'encryptedKey': encryptedKey,
      'encryptedData': encryptedData,
      'contentMd5': contentMd5,
      'timestamp': timestamp,
    };
  }

  Map<String, dynamic> toQueryMap() {
    return <String, dynamic>{
      'encryptedKey': encryptedKey,
      'encryptedData': encryptedData,
      'contentMd5': contentMd5,
      'timestamp': timestamp?.toString(),
    };
  }
}

class TransferEncryptTransportMessage {
  const TransferEncryptTransportMessage({
    required this.transferPayload,
    required this.originalContentType,
  });

  final String transferPayload;
  final String? originalContentType;

  Map<String, dynamic> toJson() {
    return <String, dynamic>{
      'transferPayload': transferPayload,
      'originalContentType': originalContentType,
    };
  }

  Map<String, dynamic> toQueryMap() {
    return <String, dynamic>{
      'transferPayload': transferPayload,
      'originalContentType': originalContentType,
    };
  }
}
