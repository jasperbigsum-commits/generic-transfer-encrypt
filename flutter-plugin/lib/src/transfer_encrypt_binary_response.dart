import 'dart:typed_data';

import 'package:http_parser/http_parser.dart';

/// Binary download result with optional integrity metadata.
class TransferEncryptBinaryResponse {
  const TransferEncryptBinaryResponse({
    required this.bytes,
    required this.statusCode,
    required this.headers,
    this.contentType,
    this.contentMd5,
  });

  final Uint8List bytes;
  final int statusCode;
  final Map<String, String> headers;
  final MediaType? contentType;
  final String? contentMd5;
}
