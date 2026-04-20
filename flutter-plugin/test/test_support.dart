import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;

class CaptureClient extends http.BaseClient {
  CaptureClient(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request) _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) {
    return _handler(request);
  }
}

class FakeDioAdapter implements HttpClientAdapter {
  FakeDioAdapter(this._handler);

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

http.StreamedResponse jsonResponse(
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

http.StreamedResponse binaryResponse(
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

String asciiToHex(String text) {
  return text.codeUnits
      .map((value) => value.toRadixString(16).padLeft(2, '0'))
      .join();
}

String encodeTransferPayload(Map<String, dynamic> envelope) {
  return base64Url.encode(utf8.encode(jsonEncode(envelope))).replaceAll('=', '');
}

Map<String, dynamic> decodeTransferPayload(String payload) {
  var normalized = payload;
  while (normalized.length % 4 != 0) {
    normalized += '=';
  }
  return Map<String, dynamic>.from(
    jsonDecode(utf8.decode(base64Url.decode(normalized))) as Map,
  );
}

Map<String, dynamic> asJsonMap(Object? data) {
  if (data is Map<String, dynamic>) {
    return data;
  }
  if (data is String) {
    return Map<String, dynamic>.from(jsonDecode(data) as Map);
  }
  return Map<String, dynamic>.from(data as Map);
}
