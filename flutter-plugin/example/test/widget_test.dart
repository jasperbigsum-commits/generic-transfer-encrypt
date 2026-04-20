import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:generic_transfer_encrypt_flutter_example/main.dart';

void main() {
  testWidgets('renders transfer encrypt demo actions', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const TransferEncryptExampleApp());

    expect(find.text('Transfer Encrypt Flutter Example'), findsOneWidget);
    expect(find.text('Base URL'), findsOneWidget);
    expect(find.text('SM2 Public Key'), findsOneWidget);
    expect(find.text('POST JSON'), findsOneWidget);
    expect(find.text('POST Form'), findsOneWidget);
    expect(find.text('GET Query'), findsOneWidget);
    expect(find.text('POST Binary'), findsOneWidget);

    await tester.drag(find.byType(ListView), const Offset(0, -600));
    await tester.pumpAndSettle();

    expect(find.text('Request Preview'), findsOneWidget);
    expect(find.text('Response / Error'), findsOneWidget);
  });
}
