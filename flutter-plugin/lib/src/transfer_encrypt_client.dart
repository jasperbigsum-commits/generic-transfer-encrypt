import 'dart:convert';
import 'dart:math';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import 'transfer_encrypt_binary_response.dart';
import 'transfer_encrypt_exception.dart';
import 'transfer_encrypt_file.dart';
import 'transfer_encrypt_protocol.dart';

/// Flutter client for the repository transport-encryption protocol.
///
/// Protocol behavior:
/// - JSON / form / query requests are encrypted with a one-time SM4 key.
/// - The SM4 key is encrypted with the server SM2 public key.
/// - Multipart file payloads are not encrypted by default; only MD5 integrity
///   fields are added to match the Spring server plugin rules.
class TransferEncryptClient {
  TransferEncryptClient({
    required this.publicKey,
    this.baseUrl = '',
    http.Client? httpClient,
    this.multipartMd5FieldPrefix = '__md5_',
    Random? random,
  })  : _httpClient = httpClient ?? http.Client(),
        _ownsHttpClient = httpClient == null,
        _protocol = TransferEncryptProtocol(
          publicKey: publicKey,
          random: random,
        );

  final String publicKey;
  final String baseUrl;
  final String multipartMd5FieldPrefix;

  final http.Client _httpClient;
  final bool _ownsHttpClient;
  final TransferEncryptProtocol _protocol;

  /// Sends a request and returns a decoded plaintext result.
  ///
  /// Supported request modes:
  /// - `params` for encrypted GET/DELETE query strings
  /// - `form` for encrypted `application/x-www-form-urlencoded`
  /// - `json` for encrypted `application/json`
  Future<dynamic> request({
    required String url,
    String method = 'GET',
    Map<String, String>? headers,
    Map<String, dynamic>? params,
    Map<String, dynamic>? form,
    Object? json,
  }) async {
    final prepared = _prepareEncryptedRequest(
      url: url,
      method: method,
      headers: headers,
      params: params,
      form: form,
      json: json,
    );
    final response = await _httpClient.send(prepared.request);
    final streamedResponse = await http.Response.fromStream(response);
    return _decodeResponse(
      response: streamedResponse,
      sm4Key: prepared.sm4Key,
      expectBinary: false,
    );
  }

  /// Sends an encrypted JSON request.
  Future<dynamic> postJson(
    String url, {
    required Object? body,
    Map<String, String>? headers,
    String method = 'POST',
  }) {
    return request(
      url: url,
      method: method,
      headers: headers,
      json: body,
    );
  }

  /// Sends an encrypted form request.
  Future<dynamic> postForm(
    String url, {
    required Map<String, dynamic> body,
    Map<String, String>? headers,
    String method = 'POST',
  }) {
    return request(
      url: url,
      method: method,
      headers: headers,
      form: body,
    );
  }

  /// Sends an encrypted GET request.
  Future<dynamic> get(
    String url, {
    Map<String, dynamic>? params,
    Map<String, String>? headers,
  }) {
    return request(
      url: url,
      method: 'GET',
      headers: headers,
      params: params,
    );
  }

  /// Sends an encrypted DELETE request.
  Future<dynamic> delete(
    String url, {
    Map<String, dynamic>? params,
    Map<String, String>? headers,
  }) {
    return request(
      url: url,
      method: 'DELETE',
      headers: headers,
      params: params,
    );
  }

  /// Uploads one or more files without encrypting file bytes.
  ///
  /// The client automatically appends MD5 integrity fields that match the
  /// Spring plugin rules:
  /// - `__md5_<fieldName>`
  /// - `__md5_<fieldName>__<index>`
  Future<dynamic> upload(
    String url, {
    required List<TransferEncryptFile> files,
    Map<String, dynamic>? fields,
    Map<String, String>? headers,
    String method = 'POST',
  }) async {
    if (files.isEmpty) {
      throw const TransferEncryptException('未提供待上传文件');
    }

    final request = http.MultipartRequest(method.toUpperCase(), _resolveUri(url));
    request.headers.addAll(headers ?? const <String, String>{});

    if (fields != null && fields.isNotEmpty) {
      for (final entry in fields.entries) {
        final value = entry.value;
        if (value == null) {
          continue;
        }
        if (value is Iterable && value is! String) {
          throw TransferEncryptException(
            'multipart 附加字段暂不支持 Iterable：${entry.key}',
          );
        }
        request.fields[entry.key] = '$value';
      }
    }

    final grouped = <String, List<TransferEncryptFile>>{};
    for (final file in files) {
      request.files.add(file.toMultipartFile());
      grouped.putIfAbsent(file.fieldName, () => <TransferEncryptFile>[]).add(file);
    }

    for (final entry in grouped.entries) {
      final fieldName = entry.key;
      final fieldFiles = entry.value;
      if (fieldFiles.length == 1) {
        request.fields['$multipartMd5FieldPrefix$fieldName'] =
            fieldFiles.first.md5Hex;
        continue;
      }
      for (var index = 0; index < fieldFiles.length; index += 1) {
        request.fields['$multipartMd5FieldPrefix$fieldName'
            '__$index'] = fieldFiles[index].md5Hex;
      }
    }

    final streamed = await _httpClient.send(request);
    final response = await http.Response.fromStream(streamed);
    return _decodeResponse(response: response, sm4Key: null, expectBinary: false);
  }

  /// Uploads a single file using the same multipart protocol.
  Future<dynamic> uploadSingle(
    String url, {
    required TransferEncryptFile file,
    Map<String, dynamic>? fields,
    Map<String, String>? headers,
    String method = 'POST',
  }) {
    return upload(
      url,
      files: <TransferEncryptFile>[file],
      fields: fields,
      headers: headers,
      method: method,
    );
  }

  /// Sends a request and expects a binary response.
  ///
  /// Download responses are not decrypted by default because repository rules
  /// treat file transfer as integrity-only. When the response contains
  /// `X-Transfer-Content-MD5`, the bytes are verified automatically.
  Future<TransferEncryptBinaryResponse> requestBinary({
    required String url,
    String method = 'GET',
    Map<String, String>? headers,
    Map<String, dynamic>? params,
    Map<String, dynamic>? form,
    Object? json,
  }) async {
    final prepared = _prepareEncryptedRequest(
      url: url,
      method: method,
      headers: headers,
      params: params,
      form: form,
      json: json,
    );
    final streamed = await _httpClient.send(prepared.request);
    final response = await http.Response.fromStream(streamed);
    final decoded = await _decodeResponse(
      response: response,
      sm4Key: prepared.sm4Key,
      expectBinary: true,
    );
    return decoded as TransferEncryptBinaryResponse;
  }

  /// Convenience wrapper for binary downloads.
  Future<TransferEncryptBinaryResponse> download(
    String url, {
    String method = 'GET',
    Map<String, dynamic>? params,
    Map<String, String>? headers,
  }) async {
    return requestBinary(
      url: url,
      method: method,
      params: params,
      headers: headers,
    );
  }

  /// Closes the internally owned HTTP client.
  void close() {
    if (_ownsHttpClient) {
      _httpClient.close();
    }
  }

  Future<dynamic> _decodeResponse({
    required http.Response response,
    required String? sm4Key,
    required bool expectBinary,
  }) async {
    final headerMap = _lowerHeaders(response.headers);
    final statusCode = response.statusCode;
    final contentTypeHeader = headerMap['content-type'] ?? '';

    if (expectBinary) {
      final bytes = Uint8List.fromList(response.bodyBytes);
      final contentMd5 = headerMap['x-transfer-content-md5'];
      if (contentMd5 != null && contentMd5.isNotEmpty) {
        final actual = md5.convert(bytes).toString();
        if (actual.toLowerCase() != contentMd5.toLowerCase()) {
          throw const TransferEncryptException('文件 MD5 校验失败');
        }
      }
      if (statusCode >= 400) {
        final errorBody = _decodePossibleJsonError(
          bodyBytes: bytes,
          contentTypeHeader: contentTypeHeader,
          headerMap: headerMap,
          sm4Key: sm4Key,
        );
        throw TransferEncryptHttpException(
          statusCode: statusCode,
          message: '下载失败',
          headers: response.headers,
          responseBody: errorBody ?? bytes,
        );
      }
      return TransferEncryptBinaryResponse(
        bytes: bytes,
        statusCode: statusCode,
        headers: response.headers,
        contentType: _parseMediaType(contentTypeHeader),
        contentMd5: contentMd5,
      );
    }

    final bodyBytes = response.bodyBytes;
    final text = bodyBytes.isEmpty ? '' : utf8.decode(bodyBytes);
    final encrypted = headerMap['x-transfer-encrypted'] == 'true';
    final looksJson = contentTypeHeader.contains('application/json');

    dynamic decodedBody = text;
    if ((encrypted || looksJson) && text.isNotEmpty) {
      final parsed = _protocol.tryParseJson(text);
      final message = _protocol.decodeTransportMessage(parsed);
      TransferEncryptEnvelope? envelope;
      if (message != null) {
        envelope = _protocol.decodeTransferPayload(message.transferPayload);
      } else if (parsed is Map<String, dynamic> &&
          parsed['encryptedData'] is String &&
          sm4Key != null) {
        envelope = TransferEncryptEnvelope.fromJson(parsed);
      }
      if (envelope != null && sm4Key != null) {
        final plaintext = _protocol.decryptEnvelope(envelope, sm4Key);
        final originalContentType = message?.originalContentType ?? '';
        if (originalContentType.contains('application/json')) {
          decodedBody = _protocol.tryParseJson(plaintext) ?? plaintext;
        } else {
          decodedBody = plaintext;
        }
      } else {
        decodedBody = parsed ?? text;
      }
    }

    if (statusCode >= 400) {
      throw TransferEncryptHttpException(
        statusCode: statusCode,
        message: '请求失败',
        responseBody: decodedBody,
        headers: response.headers,
      );
    }

    return decodedBody;
  }

  Object? _decodePossibleJsonError({
    required Uint8List bodyBytes,
    required String contentTypeHeader,
    required Map<String, String> headerMap,
    required String? sm4Key,
  }) {
    if (!contentTypeHeader.contains('application/json') || bodyBytes.isEmpty) {
      return null;
    }

    final text = utf8.decode(bodyBytes);
    final parsed = _protocol.tryParseJson(text);
    final message = _protocol.decodeTransportMessage(parsed);
    TransferEncryptEnvelope? envelope;
    if (message != null) {
      envelope = _protocol.decodeTransferPayload(message.transferPayload);
    } else if (parsed is Map<String, dynamic> &&
        parsed['encryptedData'] is String &&
        sm4Key != null &&
        headerMap['x-transfer-encrypted'] == 'true') {
      envelope = TransferEncryptEnvelope.fromJson(parsed);
    }
    if (envelope != null &&
        sm4Key != null &&
        headerMap['x-transfer-encrypted'] == 'true') {
      final plaintext = _protocol.decryptEnvelope(envelope, sm4Key);
      if ((message?.originalContentType ?? '').contains('application/json')) {
        return _protocol.tryParseJson(plaintext) ?? plaintext;
      }
      return plaintext;
    }
    return parsed ?? text;
  }

  _PreparedRequest _prepareEncryptedRequest({
    required String url,
    required String method,
    Map<String, String>? headers,
    Map<String, dynamic>? params,
    Map<String, dynamic>? form,
    Object? json,
  }) {
    _ensurePublicKey();

    final normalizedMethod = method.toUpperCase();
    final resolvedUri = _resolveUri(url);
    final requestHeaders = <String, String>{...?headers};

    String? sm4Key;
    Uri requestUri = resolvedUri;
    Object? requestBody;

    if ((normalizedMethod == 'GET' || normalizedMethod == 'DELETE') &&
        params != null &&
        params.isNotEmpty) {
      sm4Key = _protocol.randomSm4Key();
      final message = _protocol.createTransportMessage(
        envelope: _protocol.createEnvelope(
        plaintext: _protocol.normalizeParameters(params),
        sm4Key: sm4Key,
        ),
        originalContentType: 'application/x-www-form-urlencoded',
      );
      requestUri = _protocol.appendQueryString(
        resolvedUri,
        _protocol.normalizeParameters(message.toQueryMap()),
      );
    } else if (form != null) {
      sm4Key = _protocol.randomSm4Key();
      requestHeaders['Content-Type'] =
          'application/x-www-form-urlencoded;charset=UTF-8';
      final message = _protocol.createTransportMessage(
        envelope: _protocol.createEnvelope(
          plaintext: _protocol.normalizeParameters(form),
          sm4Key: sm4Key,
        ),
        originalContentType: 'application/x-www-form-urlencoded',
      );
      requestBody = _protocol.normalizeParameters(
        message.toQueryMap(),
      );
    } else if (json != null) {
      sm4Key = _protocol.randomSm4Key();
      requestHeaders['Content-Type'] = 'application/json;charset=UTF-8';
      requestBody = jsonEncode(_protocol.createTransportMessage(
        envelope: _protocol.createEnvelope(
          plaintext: jsonEncode(json),
          sm4Key: sm4Key,
        ),
        originalContentType: 'application/json',
      ).toJson());
    }

    final rawRequest = http.Request(normalizedMethod, requestUri)
      ..headers.addAll(requestHeaders);
    if (requestBody != null) {
      rawRequest.body = requestBody.toString();
    }

    return _PreparedRequest(request: rawRequest, sm4Key: sm4Key);
  }

  Map<String, String> _lowerHeaders(Map<String, String> headers) {
    return headers.map(
      (key, value) => MapEntry(key.toLowerCase(), value),
    );
  }

  Uri _resolveUri(String path) {
    final trimmed = path.trim();
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return Uri.parse(trimmed);
    }
    if (baseUrl.isEmpty) {
      return Uri.parse(trimmed);
    }
    return Uri.parse(baseUrl).resolve(trimmed);
  }

  MediaType? _parseMediaType(String rawValue) {
    if (rawValue.isEmpty) {
      return null;
    }
    try {
      return MediaType.parse(rawValue);
    } catch (_) {
      return null;
    }
  }

  void _ensurePublicKey() {
    if (publicKey.trim().isEmpty) {
      throw const TransferEncryptException('publicKey 未配置');
    }
  }
}

class _PreparedRequest {
  const _PreparedRequest({
    required this.request,
    required this.sm4Key,
  });

  final http.Request request;
  final String? sm4Key;
}
