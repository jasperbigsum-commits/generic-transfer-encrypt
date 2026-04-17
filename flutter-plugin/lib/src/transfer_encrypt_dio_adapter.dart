import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:http_parser/http_parser.dart';

import 'transfer_encrypt_binary_response.dart';
import 'transfer_encrypt_exception.dart';
import 'transfer_encrypt_file.dart';
import 'transfer_encrypt_protocol.dart';

/// Dio-based transport-encryption adapter for Flutter projects that already
/// standardize on Dio as the HTTP stack.
class TransferEncryptDioAdapter {
  TransferEncryptDioAdapter({
    required this.dio,
    required this.publicKey,
    this.baseUrl = '',
    this.multipartMd5FieldPrefix = '__md5_',
  }) : _protocol = TransferEncryptProtocol(publicKey: publicKey);

  final Dio dio;
  final String publicKey;
  final String baseUrl;
  final String multipartMd5FieldPrefix;
  final TransferEncryptProtocol _protocol;

  /// Sends a transport-encrypted request and returns decoded plaintext data.
  Future<dynamic> request({
    required String url,
    String method = 'GET',
    Map<String, dynamic>? params,
    Map<String, dynamic>? form,
    Object? json,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
    Options? options,
  }) async {
    final prepared = _prepareRequest(
      url: url,
      method: method,
      params: params,
      form: form,
      json: json,
      headers: headers,
      options: options,
      responseType: ResponseType.plain,
    );

    try {
      final response = await dio.request<dynamic>(
        prepared.path,
        data: prepared.data,
        queryParameters: prepared.queryParameters,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
        options: prepared.options,
      );
      return _decodeResponse(
        response: response,
        sm4Key: prepared.sm4Key,
        expectBinary: false,
      );
    } on DioException catch (error) {
      throw _mapDioException(error, prepared.sm4Key, false);
    }
  }

  /// Sends a request that expects binary bytes back.
  Future<TransferEncryptBinaryResponse> requestBinary({
    required String url,
    String method = 'GET',
    Map<String, dynamic>? params,
    Map<String, dynamic>? form,
    Object? json,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
    Options? options,
  }) async {
    final prepared = _prepareRequest(
      url: url,
      method: method,
      params: params,
      form: form,
      json: json,
      headers: headers,
      options: options,
      responseType: ResponseType.bytes,
    );

    try {
      final response = await dio.request<List<int>>(
        prepared.path,
        data: prepared.data,
        queryParameters: prepared.queryParameters,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
        options: prepared.options,
      );
      final decoded = _decodeResponse(
        response: response,
        sm4Key: prepared.sm4Key,
        expectBinary: true,
      );
      return decoded as TransferEncryptBinaryResponse;
    } on DioException catch (error) {
      throw _mapDioException(error, prepared.sm4Key, true);
    }
  }

  /// Convenience wrapper for encrypted JSON requests.
  Future<dynamic> postJson(
    String url, {
    required Object? body,
    Map<String, dynamic>? headers,
    String method = 'POST',
    CancelToken? cancelToken,
    Options? options,
  }) {
    return request(
      url: url,
      method: method,
      json: body,
      headers: headers,
      cancelToken: cancelToken,
      options: options,
    );
  }

  /// Convenience wrapper for encrypted form requests.
  Future<dynamic> postForm(
    String url, {
    required Map<String, dynamic> body,
    Map<String, dynamic>? headers,
    String method = 'POST',
    CancelToken? cancelToken,
    Options? options,
  }) {
    return request(
      url: url,
      method: method,
      form: body,
      headers: headers,
      cancelToken: cancelToken,
      options: options,
    );
  }

  /// Convenience wrapper for encrypted GET requests.
  Future<dynamic> get(
    String url, {
    Map<String, dynamic>? params,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
    Options? options,
  }) {
    return request(
      url: url,
      method: 'GET',
      params: params,
      headers: headers,
      cancelToken: cancelToken,
      options: options,
    );
  }

  /// Convenience wrapper for encrypted DELETE requests.
  Future<dynamic> delete(
    String url, {
    Map<String, dynamic>? params,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
    Options? options,
  }) {
    return request(
      url: url,
      method: 'DELETE',
      params: params,
      headers: headers,
      cancelToken: cancelToken,
      options: options,
    );
  }

  /// Uploads one or more files without encrypting file bytes.
  Future<dynamic> upload({
    required String url,
    required List<TransferEncryptFile> files,
    Map<String, dynamic>? fields,
    Map<String, dynamic>? headers,
    String method = 'POST',
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
    Options? options,
  }) async {
    if (files.isEmpty) {
      throw const TransferEncryptException('未提供待上传文件');
    }

    final formData = FormData();
    final grouped = <String, List<TransferEncryptFile>>{};

    if (fields != null && fields.isNotEmpty) {
      for (final entry in fields.entries) {
        if (entry.value != null) {
          formData.fields.add(MapEntry(entry.key, '${entry.value}'));
        }
      }
    }

    for (final file in files) {
      grouped.putIfAbsent(file.fieldName, () => <TransferEncryptFile>[]).add(file);
      formData.files.add(
        MapEntry(
          file.fieldName,
          MultipartFile.fromBytes(
            file.bytes,
            filename: file.filename,
            contentType: file.contentType,
          ),
        ),
      );
    }

    for (final entry in grouped.entries) {
      if (entry.value.length == 1) {
        formData.fields.add(
          MapEntry(
            '$multipartMd5FieldPrefix${entry.key}',
            entry.value.first.md5Hex,
          ),
        );
        continue;
      }
      for (var index = 0; index < entry.value.length; index += 1) {
        formData.fields.add(
          MapEntry(
            '$multipartMd5FieldPrefix${entry.key}__$index',
            entry.value[index].md5Hex,
          ),
        );
      }
    }

    final mergedOptions = _mergeOptions(
      method: method,
      headers: headers,
      responseType: ResponseType.plain,
      options: options,
    );

    try {
      final response = await dio.request<dynamic>(
        _resolvePath(url),
        data: formData,
        cancelToken: cancelToken,
        onSendProgress: onSendProgress,
        onReceiveProgress: onReceiveProgress,
        options: mergedOptions,
      );
      return _decodeResponse(
        response: response,
        sm4Key: null,
        expectBinary: false,
      );
    } on DioException catch (error) {
      throw _mapDioException(error, null, false);
    }
  }

  /// Convenience wrapper for single-file upload.
  Future<dynamic> uploadSingle({
    required String url,
    required TransferEncryptFile file,
    Map<String, dynamic>? fields,
    Map<String, dynamic>? headers,
    String method = 'POST',
    CancelToken? cancelToken,
    ProgressCallback? onSendProgress,
    ProgressCallback? onReceiveProgress,
    Options? options,
  }) {
    return upload(
      url: url,
      files: <TransferEncryptFile>[file],
      fields: fields,
      headers: headers,
      method: method,
      cancelToken: cancelToken,
      onSendProgress: onSendProgress,
      onReceiveProgress: onReceiveProgress,
      options: options,
    );
  }

  _PreparedDioRequest _prepareRequest({
    required String url,
    required String method,
    Map<String, dynamic>? params,
    Map<String, dynamic>? form,
    Object? json,
    Map<String, dynamic>? headers,
    Options? options,
    required ResponseType responseType,
  }) {
    _ensurePublicKey();

    final normalizedMethod = method.toUpperCase();
    final resolved = _resolvePath(url);
    final requestHeaders = <String, dynamic>{...?headers};
    Map<String, dynamic>? queryParameters;
    Object? data;
    String? sm4Key;

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
      queryParameters = message.toQueryMap();
    } else if (form != null) {
      sm4Key = _protocol.randomSm4Key();
      requestHeaders[Headers.contentTypeHeader] =
          Headers.formUrlEncodedContentType;
      final message = _protocol.createTransportMessage(
        envelope: _protocol.createEnvelope(
          plaintext: _protocol.normalizeParameters(form),
          sm4Key: sm4Key,
        ),
        originalContentType: 'application/x-www-form-urlencoded',
      );
      data = _protocol.normalizeParameters(message.toQueryMap());
    } else if (json != null) {
      sm4Key = _protocol.randomSm4Key();
      requestHeaders[Headers.contentTypeHeader] = Headers.jsonContentType;
      data = _protocol.createTransportMessage(
        envelope: _protocol.createEnvelope(
          plaintext: jsonEncode(json),
          sm4Key: sm4Key,
        ),
        originalContentType: 'application/json',
      ).toJson();
    }

    return _PreparedDioRequest(
      path: resolved,
      data: data,
      queryParameters: queryParameters,
      sm4Key: sm4Key,
      options: _mergeOptions(
        method: normalizedMethod,
        headers: requestHeaders,
        responseType: responseType,
        options: options,
      ),
    );
  }

  Options _mergeOptions({
    required String method,
    required Map<String, dynamic>? headers,
    required ResponseType responseType,
    Options? options,
  }) {
    final mergedHeaders = <String, dynamic>{
      ...?options?.headers,
      ...?headers,
    };

    return (options ?? Options()).copyWith(
      method: method,
      headers: mergedHeaders,
      responseType: responseType,
      validateStatus: options?.validateStatus ?? (_) => true,
    );
  }

  dynamic _decodeResponse({
    required Response<dynamic> response,
    required String? sm4Key,
    required bool expectBinary,
  }) {
    final headers = _flattenHeaders(response.headers.map);
    final statusCode = response.statusCode ?? 200;
    final contentTypeHeader = headers['content-type'] ?? '';

    if (expectBinary) {
      final bytes = _extractResponseBytes(response.data);
      final contentMd5 = headers['x-transfer-content-md5'];
      if (contentMd5 != null && contentMd5.isNotEmpty) {
        final actual = md5.convert(bytes).toString();
        if (actual.toLowerCase() != contentMd5.toLowerCase()) {
          throw const TransferEncryptException('文件 MD5 校验失败');
        }
      }

      if (statusCode >= 400) {
        final errorBody = _decodePossibleJsonError(
          bytes: bytes,
          contentTypeHeader: contentTypeHeader,
          headers: headers,
          sm4Key: sm4Key,
        );
        throw TransferEncryptHttpException(
          statusCode: statusCode,
          message: '下载失败',
          headers: Map<String, String>.from(headers),
          responseBody: errorBody ?? bytes,
        );
      }

      return TransferEncryptBinaryResponse(
        bytes: bytes,
        statusCode: statusCode,
        headers: Map<String, String>.from(headers),
        contentType: _parseMediaType(contentTypeHeader),
        contentMd5: contentMd5,
      );
    }

    final text = _responseAsText(response.data);
    final encrypted = headers['x-transfer-encrypted'] == 'true';
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
        if ((message?.originalContentType ?? '').contains('application/json')) {
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
        headers: Map<String, String>.from(headers),
        responseBody: decodedBody,
      );
    }

    return decodedBody;
  }

  TransferEncryptException _mapDioException(
    DioException error,
    String? sm4Key,
    bool expectBinary,
  ) {
    if (error.response != null) {
      try {
        return _decodeResponse(
          response: error.response!,
          sm4Key: sm4Key,
          expectBinary: expectBinary,
        ) as TransferEncryptException;
      } on TransferEncryptException catch (decoded) {
        return decoded;
      }
    }
    return TransferEncryptException(
      error.message ?? 'Dio 请求失败',
      cause: error,
    );
  }

  Object? _decodePossibleJsonError({
    required Uint8List bytes,
    required String contentTypeHeader,
    required Map<String, String> headers,
    required String? sm4Key,
  }) {
    if (!contentTypeHeader.contains('application/json') || bytes.isEmpty) {
      return null;
    }
    final text = utf8.decode(bytes);
    final parsed = _protocol.tryParseJson(text);
    final message = _protocol.decodeTransportMessage(parsed);
    TransferEncryptEnvelope? envelope;
    if (message != null) {
      envelope = _protocol.decodeTransferPayload(message.transferPayload);
    } else if (parsed is Map<String, dynamic> &&
        parsed['encryptedData'] is String &&
        sm4Key != null &&
        headers['x-transfer-encrypted'] == 'true') {
      envelope = TransferEncryptEnvelope.fromJson(parsed);
    }
    if (envelope != null &&
        sm4Key != null &&
        headers['x-transfer-encrypted'] == 'true') {
      final plaintext = _protocol.decryptEnvelope(envelope, sm4Key);
      if ((message?.originalContentType ?? '').contains('application/json')) {
        return _protocol.tryParseJson(plaintext) ?? plaintext;
      }
      return plaintext;
    }
    return parsed ?? text;
  }

  Uint8List _extractResponseBytes(dynamic data) {
    if (data is Uint8List) {
      return data;
    }
    if (data is List<int>) {
      return Uint8List.fromList(data);
    }
    if (data is String) {
      return Uint8List.fromList(utf8.encode(data));
    }
    if (data == null) {
      return Uint8List(0);
    }
    return Uint8List.fromList(utf8.encode(data.toString()));
  }

  String _responseAsText(dynamic data) {
    if (data == null) {
      return '';
    }
    if (data is String) {
      return data;
    }
    if (data is Uint8List) {
      return utf8.decode(data);
    }
    if (data is List<int>) {
      return utf8.decode(data);
    }
    if (data is Map || data is List) {
      return jsonEncode(data);
    }
    return data.toString();
  }

  Map<String, String> _flattenHeaders(Map<String, List<String>> headers) {
    return headers.map(
      (key, value) => MapEntry(key.toLowerCase(), value.join(',')),
    );
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

  String _resolvePath(String path) {
    final trimmed = path.trim();
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return trimmed;
    }
    if (baseUrl.isEmpty) {
      return trimmed;
    }
    return Uri.parse(baseUrl).resolve(trimmed).toString();
  }

  void _ensurePublicKey() {
    if (publicKey.trim().isEmpty) {
      throw const TransferEncryptException('publicKey 未配置');
    }
  }
}

class _PreparedDioRequest {
  const _PreparedDioRequest({
    required this.path,
    required this.options,
    required this.sm4Key,
    this.data,
    this.queryParameters,
  });

  final String path;
  final Object? data;
  final Map<String, dynamic>? queryParameters;
  final String? sm4Key;
  final Options options;
}
