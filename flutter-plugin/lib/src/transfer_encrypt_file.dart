import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:mime/mime.dart';

/// File payload used by multipart upload APIs.
class TransferEncryptFile {
  TransferEncryptFile({
    required this.fieldName,
    required List<int> bytes,
    required this.filename,
    this.contentType,
  }) : bytes = Uint8List.fromList(bytes);

  /// Creates an in-memory text file using UTF-8 encoding.
  factory TransferEncryptFile.fromText({
    required String fieldName,
    required String filename,
    required String text,
    MediaType? contentType,
  }) {
    return TransferEncryptFile(
      fieldName: fieldName,
      filename: filename,
      bytes: utf8.encode(text),
      contentType: contentType ?? MediaType('text', 'plain', <String, String>{
        'charset': 'utf-8',
      }),
    );
  }

  final String fieldName;
  final Uint8List bytes;
  final String filename;
  final MediaType? contentType;

  /// Hex MD5 of the raw file bytes.
  String get md5Hex => md5.convert(bytes).toString();

  /// Converts the object into `package:http` multipart content.
  http.MultipartFile toMultipartFile() {
    return http.MultipartFile.fromBytes(
      fieldName,
      bytes,
      filename: filename,
      contentType: contentType ?? _guessMediaType(filename, bytes),
    );
  }

  static MediaType? _guessMediaType(String filename, List<int> bytes) {
    final mimeType = lookupMimeType(filename, headerBytes: bytes);
    if (mimeType == null) {
      return null;
    }
    return MediaType.parse(mimeType);
  }
}
