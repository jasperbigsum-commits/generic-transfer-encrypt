import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:generic_transfer_encrypt_flutter/flutter_transfer_encrypt.dart';

void main() {
  runApp(const TransferEncryptExampleApp());
}

class TransferEncryptExampleApp extends StatelessWidget {
  const TransferEncryptExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Transfer Encrypt Example',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0B7285)),
        useMaterial3: true,
      ),
      home: const TransferEncryptDemoPage(),
    );
  }
}

class TransferEncryptDemoPage extends StatefulWidget {
  const TransferEncryptDemoPage({super.key});

  @override
  State<TransferEncryptDemoPage> createState() =>
      _TransferEncryptDemoPageState();
}

class _TransferEncryptDemoPageState extends State<TransferEncryptDemoPage> {
  final TextEditingController _baseUrlController =
      TextEditingController(text: 'http://localhost:8080');
  final TextEditingController _publicKeyController = TextEditingController();

  bool _running = false;
  String _status = 'Idle';
  String _lastAction = '尚未执行';
  String _requestPreview = '点击任一按钮后，这里会显示请求示例。';
  String _output = '点击下方按钮后，这里会显示请求结果。';

  @override
  void dispose() {
    _baseUrlController.dispose();
    _publicKeyController.dispose();
    super.dispose();
  }

  Future<void> _runJsonDemo() {
    return _runDemo(
      actionName: 'POST JSON',
      requestPreview: const JsonEncoder.withIndent('  ').convert(
        <String, dynamic>{
          'method': 'POST',
          'path': '/api/json',
          'body': <String, dynamic>{
            'name': 'flutter-client',
            'time': '2026-04-19T00:00:00.000Z',
          },
        },
      ),
      action: (client) {
        return client.postJson(
          '/api/json',
          body: <String, dynamic>{
            'name': 'flutter-client',
            'time': DateTime.now().toIso8601String(),
          },
        );
      },
    );
  }

  Future<void> _runFormDemo() {
    return _runDemo(
      actionName: 'POST Form',
      requestPreview: const JsonEncoder.withIndent('  ').convert(
        <String, dynamic>{
          'method': 'POST',
          'path': '/api/form',
          'body': <String, dynamic>{
            'name': 'flutter-form',
            'age': 18,
          },
        },
      ),
      action: (client) {
        return client.postForm(
          '/api/form',
          body: <String, dynamic>{'name': 'flutter-form', 'age': 18},
        );
      },
    );
  }

  Future<void> _runQueryDemo() {
    return _runDemo(
      actionName: 'GET Query',
      requestPreview: const JsonEncoder.withIndent('  ').convert(
        <String, dynamic>{
          'method': 'GET',
          'path': '/api/query',
          'params': <String, dynamic>{'name': 'flutter-query'},
        },
      ),
      action: (client) {
        return client.get(
          '/api/query',
          params: <String, dynamic>{'name': 'flutter-query'},
        );
      },
    );
  }

  Future<void> _runBinaryDemo() {
    return _runDemo(
      actionName: 'POST Binary',
      requestPreview: const JsonEncoder.withIndent('  ').convert(
        <String, dynamic>{
          'method': 'POST',
          'path': '/api/export',
          'body': <String, dynamic>{'bizId': 1001},
        },
      ),
      action: (client) async {
        final response = await client.requestBinary(
          url: '/api/export',
          method: 'POST',
          json: <String, dynamic>{'bizId': 1001},
        );
        return <String, dynamic>{
          'statusCode': response.statusCode,
          'contentType': response.contentType?.toString(),
          'contentMd5': response.contentMd5,
          'byteLength': response.bytes.length,
          'previewBase64': base64Encode(
            response.bytes.length > 48
                ? response.bytes.sublist(0, 48)
                : response.bytes,
          ),
        };
      },
    );
  }

  Future<void> _runDemo({
    required String actionName,
    required String requestPreview,
    required Future<Object?> Function(TransferEncryptClient client) action,
  }) async {
    final client = TransferEncryptClient(
      baseUrl: _baseUrlController.text.trim(),
      publicKey: _publicKeyController.text.trim(),
    );

    setState(() {
      _running = true;
      _status = 'Running';
      _lastAction = actionName;
      _requestPreview = requestPreview;
      _output = '请求发送中，请稍候...';
    });

    try {
      final result = await action(client);
      setState(() {
        _status = 'Success';
        _output = _formatResult(result);
      });
    } on Object catch (error) {
      setState(() {
        _status = 'Error';
        _output = error.toString();
      });
    } finally {
      client.close();
      if (mounted) {
        setState(() {
          _running = false;
        });
      }
    }
  }

  void _clearOutput() {
    setState(() {
      _status = 'Idle';
      _lastAction = '尚未执行';
      _requestPreview = '点击任一按钮后，这里会显示请求示例。';
      _output = '点击下方按钮后，这里会显示请求结果。';
    });
  }

  String _formatResult(Object? result) {
    if (result is Map || result is List) {
      return const JsonEncoder.withIndent('  ').convert(result);
    }
    return result?.toString() ?? '';
  }

  Color _statusColor(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    switch (_status) {
      case 'Success':
        return colorScheme.primaryContainer;
      case 'Error':
        return colorScheme.errorContainer;
      case 'Running':
        return colorScheme.secondaryContainer;
      case 'Idle':
        return colorScheme.surfaceContainerHighest;
    }
    return colorScheme.surfaceContainerHighest;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Transfer Encrypt Flutter Example')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: <Widget>[
          Text(
            '本示例用于联调仓库里的 Spring 加密接口，默认演示 JSON、Form、Query 和二进制导出四类请求。',
            style: Theme.of(context).textTheme.bodyLarge,
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: const <Widget>[
              _TagChip(label: 'POST /api/json'),
              _TagChip(label: 'POST /api/form'),
              _TagChip(label: 'GET /api/query'),
              _TagChip(label: 'POST /api/export'),
            ],
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _baseUrlController,
            decoration: const InputDecoration(
              labelText: 'Base URL',
              hintText: 'http://localhost:8080',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _publicKeyController,
            minLines: 3,
            maxLines: 5,
            decoration: const InputDecoration(
              labelText: 'SM2 Public Key',
              hintText: '请粘贴服务端下发的 SM2 公钥',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            elevation: 0,
            color: _statusColor(context),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text('当前状态: $_status'),
                  const SizedBox(height: 6),
                  Text('最近操作: $_lastAction'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: <Widget>[
              FilledButton.icon(
                onPressed: _running ? null : _runJsonDemo,
                icon: const Icon(Icons.data_object),
                label: const Text('POST JSON'),
              ),
              FilledButton.tonalIcon(
                onPressed: _running ? null : _runFormDemo,
                icon: const Icon(Icons.post_add),
                label: const Text('POST Form'),
              ),
              OutlinedButton.icon(
                onPressed: _running ? null : _runQueryDemo,
                icon: const Icon(Icons.search),
                label: const Text('GET Query'),
              ),
              OutlinedButton.icon(
                onPressed: _running ? null : _runBinaryDemo,
                icon: const Icon(Icons.download),
                label: const Text('POST Binary'),
              ),
              TextButton.icon(
                onPressed: _running ? null : _clearOutput,
                icon: const Icon(Icons.clear_all),
                label: const Text('Clear Output'),
              ),
            ],
          ),
          const SizedBox(height: 20),
          _ResultPanel(
            title: 'Request Preview',
            description: '当前按钮对应的请求示例，便于和服务端联调时对照接口路径与参数。',
            content: _requestPreview,
          ),
          const SizedBox(height: 16),
          _ResultPanel(
            title: 'Response / Error',
            description: '请求成功时展示结果，失败时展示异常文本。',
            content: _output,
          ),
        ],
      ),
    );
  }
}

class _TagChip extends StatelessWidget {
  const _TagChip({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Text(label),
      ),
    );
  }
}

class _ResultPanel extends StatelessWidget {
  const _ResultPanel({
    required this.title,
    required this.description,
    required this.content,
  });

  final String title;
  final String description;
  final String content;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFFF1F3F5),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFD0D7DE)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            title,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 6),
          Text(description),
          const SizedBox(height: 12),
          SelectableText(content),
        ],
      ),
    );
  }
}
