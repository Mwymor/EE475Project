#!/bin/bash
EXIT_STATUS=0
echo "##### Running Black #####"
black --version
black .
echo "##### Running flake8 #####"
flake8 . --exclude .venv 
exit $EXIT_STATUS