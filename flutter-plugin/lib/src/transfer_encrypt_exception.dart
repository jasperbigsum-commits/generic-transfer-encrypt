/// Base exception for Flutter transport-encryption failures.
class TransferEncryptException implements Exception {
  const TransferEncryptException(this.message, {this.cause});

  final String message;
  final Object? cause;

  @override
  String toString() => 'TransferEncryptException: $message';
}

/// HTTP-layer exception that keeps decoded response payload when available.
class TransferEncryptHttpException extends TransferEncryptException {
  const TransferEncryptHttpException({
    required this.statusCode,
    required String message,
    this.responseBody,
    this.headers = const <String, String>{},
  }) : super(message);

  final int statusCode;
  final Object? responseBody;
  final Map<String, String> headers;

  @override
  String toString() => 'TransferEncryptHttpException($statusCode): $message';
}
